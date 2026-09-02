package reiz.miniecommerce.modules.payments.application.services;

import reiz.miniecommerce.modules.orders.application.services.OrderService;
import reiz.miniecommerce.modules.orders.core.entities.Order;
import reiz.miniecommerce.modules.orders.core.entities.OrderStatus;
import reiz.miniecommerce.modules.orders.core.interfaces.repositories.OrderRepository;
import reiz.miniecommerce.modules.payments.core.entities.GatewayPayment;
import reiz.miniecommerce.modules.payments.core.entities.OpenedCharge;
import reiz.miniecommerce.modules.payments.core.entities.Payment;
import reiz.miniecommerce.modules.payments.core.entities.PaymentIntent;
import reiz.miniecommerce.modules.payments.core.exceptions.OrderAlreadyPaidException;
import reiz.miniecommerce.modules.payments.core.exceptions.PaymentNotFoundException;
import reiz.miniecommerce.modules.payments.core.interfaces.repositories.NotificationDeduplicator;
import reiz.miniecommerce.modules.payments.core.interfaces.repositories.PaymentGateway;
import reiz.miniecommerce.modules.payments.core.interfaces.repositories.PaymentRepository;
import reiz.miniecommerce.modules.users.core.interfaces.repositories.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentService {

    private final PaymentRepository paymentRepository;
    private final PaymentGateway paymentGateway;
    private final NotificationDeduplicator deduplicator;
    private final OrderRepository orderRepository;
    private final OrderService orderService;
    private final UserRepository userRepository;

    /**
     * Opens a charge for an order that is still awaiting payment.
     *
     * <p>The amount is the sum of the order's frozen line prices, computed here — never taken
     * from the caller.
     */
    @Transactional
    public OpenedCharge openCharge(UUID orderId, UUID callerId, boolean asOwner) {
        Order order = orderService.visibleOrder(orderId, callerId, asOwner);

        if (order.getStatus() != OrderStatus.PENDING) {
            throw new OrderAlreadyPaidException(orderId);
        }

        Payment payment = paymentRepository.save(Payment.builder()
                .amount(orderService.totalOf(orderId))
                .paid(false)
                .build());

        order.setPaymentId(payment.getId());
        orderRepository.save(order);

        String email = userRepository.findById(order.getUserId())
                .map(user -> user.getEmail())
                .orElse(null);

        PaymentIntent intent = paymentGateway.openCharge(
                payment.getId(), "Pedido " + orderId, payment.getAmount(), email);

        return new OpenedCharge(payment, intent);
    }

    @Transactional(readOnly = true)
    public Payment visiblePayment(UUID paymentId, UUID callerId, boolean asOwner) {
        Payment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new PaymentNotFoundException(paymentId));

        if (asOwner) {
            return payment;
        }
        boolean mine = orderRepository.findByPaymentId(paymentId)
                .filter(order -> callerId.equals(order.getUserId()))
                .isPresent();

        if (!mine) {
            throw new PaymentNotFoundException(paymentId);
        }
        return payment;
    }

    /**
     * Settles a payment from a gateway notification.
     *
     * <p>The notification is treated as a hint, not as evidence: the payment is read back
     * from the gateway before anything is marked as paid, so a forged or stale message cannot
     * settle an order on its own.
     *
     * <p>Every outcome other than a bad signature is a success from the caller's point of
     * view. Mercado Pago retries anything that is not 2xx, so an unknown or duplicate
     * notification has to be accepted quietly rather than answered with an error.
     */
    @Transactional
    public void settleFromNotification(String gatewayPaymentId) {
        if (!deduplicator.claim(gatewayPaymentId)) {
            log.info("Notification {} already handled; ignoring the retry", gatewayPaymentId);
            return;
        }

        // The claim is taken before the work so two simultaneous retries cannot both settle
        // the payment. But it is only allowed to stand if the work finishes: giving it back
        // on failure is what keeps a retry able to succeed. Without this, one transient error
        // would make every redelivery look like a duplicate and the payment would never
        // settle, leaving an order the customer already paid for stuck on PENDING.
        try {
            process(gatewayPaymentId);
        } catch (RuntimeException e) {
            deduplicator.release(gatewayPaymentId);
            throw e;
        }
    }

    private void process(String gatewayPaymentId) {
        Optional<GatewayPayment> found = paymentGateway.findPayment(gatewayPaymentId);
        if (found.isEmpty()) {
            log.warn("Mercado Pago has no payment {}", gatewayPaymentId);
            return;
        }

        GatewayPayment gatewayPayment = found.get();
        if (!gatewayPayment.isApproved()) {
            log.info("Payment {} is {}; nothing to settle", gatewayPaymentId, gatewayPayment.getStatus());
            return;
        }

        UUID paymentId = parseReference(gatewayPayment.getExternalReference());
        if (paymentId == null) {
            log.warn("Payment {} carries no usable external reference", gatewayPaymentId);
            return;
        }

        paymentRepository.findById(paymentId).ifPresentOrElse(
                payment -> markPaid(payment, gatewayPayment),
                () -> log.warn("No local payment {} for gateway payment {}", paymentId, gatewayPaymentId));
    }

    /**
     * Settles a charge by asking the gateway about it directly, instead of waiting to be told.
     *
     * <p>The webhook answers 200 before doing the work, so once it has answered the gateway
     * will not retry on our behalf. If that background work fails and no further notification
     * arrives, an order the customer already paid for stays PENDING and nobody notices. This
     * is the sweep that catches those.
     *
     * @return true when this call settled the payment
     */
    @Transactional
    public boolean reconcile(UUID paymentId) {
        Payment payment = paymentRepository.findById(paymentId).orElse(null);
        if (payment == null || payment.isPaid()) {
            return false;
        }

        Optional<GatewayPayment> approved = paymentGateway.findByExternalReference(paymentId);
        if (approved.isEmpty()) {
            return false;
        }

        log.warn("Payment {} was approved at the gateway but never settled here; "
                + "settling it from reconciliation", paymentId);
        markPaid(payment, approved.get());
        return true;
    }

    private void markPaid(Payment payment, GatewayPayment gatewayPayment) {
        if (payment.isPaid()) {
            return;
        }
        payment.markAsPaid(gatewayPayment.getGatewayId());
        paymentRepository.save(payment);

        orderRepository.findByPaymentId(payment.getId()).ifPresent(order -> {
            if (order.getStatus().canTransitionTo(OrderStatus.PAID)) {
                orderService.changeStatus(order.getId(), OrderStatus.PAID);
            }
        });
    }

    private UUID parseReference(String reference) {
        try {
            return reference == null ? null : UUID.fromString(reference);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
