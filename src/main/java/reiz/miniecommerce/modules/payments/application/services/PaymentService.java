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
import org.springframework.transaction.support.TransactionTemplate;

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
    private final TransactionTemplate transactions;

    /**
     * Opens a charge for an order that is still awaiting payment.
     *
     * <p>The amount is the sum of the order's frozen line prices, computed here — never taken
     * from the caller.
     *
     * <p>Deliberately not annotated. The row is written and committed first, and only then is
     * Mercado Pago called: the pool is five connections wide and the gateway is allowed ten
     * seconds, so a call held between BEGIN and COMMIT would let a slow provider drain the
     * pool and stall requests that have nothing to do with payment. The transaction is opened
     * with a template instead of by delegating to another {@code @Transactional} method of
     * this class, because that call would be made on {@code this}, bypass the proxy and run
     * with no transaction at all — the guard below and the row that follows it would stop
     * being one unit, which is the very thing they have to be.
     *
     * <p>The cost of committing first is that a gateway failure leaves a payments row with no
     * charge behind it. That row is inert: nothing at Mercado Pago references it, so
     * reconciliation can never find it and it can never settle. The extended reservation
     * survives too, which errs in the customer's favour — the stock stays theirs while they
     * retry, and the sweep still reclaims it at the longer deadline. Undoing either in a
     * compensating write would buy nothing and could fail on its own.
     */
    public OpenedCharge openCharge(UUID orderId, UUID callerId, boolean asOwner) {
        RegisteredCharge charge = transactions.execute(status -> registerCharge(orderId, callerId, asOwner));

        PaymentIntent intent = paymentGateway.openCharge(
                charge.payment().getId(), "Pedido " + orderId, charge.payment().getAmount(), charge.payerEmail());

        return new OpenedCharge(charge.payment(), intent);
    }

    /**
     * The database half of opening a charge: everything that has to be all-or-nothing.
     *
     * <p>An order that was paid while this ran must not also be charged here, so the check
     * for PENDING and the row it authorizes commit together or not at all.
     */
    private RegisteredCharge registerCharge(UUID orderId, UUID callerId, boolean asOwner) {
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

        // The customer reached the payment screen, so the short reservation from checkout is
        // no longer the right deadline — see OrderService#extendReservation.
        orderService.extendReservation(orderId);

        String email = userRepository.findById(order.getUserId())
                .map(user -> user.getEmail())
                .orElse(null);

        return new RegisteredCharge(payment, email);
    }

    /** What the transaction hands to the gateway call: the row to charge and who to bill. */
    private record RegisteredCharge(Payment payment, String payerEmail) {
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
