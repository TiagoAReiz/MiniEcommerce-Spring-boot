package reiz.miniecommerce.modules.payments.application.services;

import reiz.miniecommerce.modules.payments.adapters.out.mercadopago.MercadoPagoProperties;
import reiz.miniecommerce.modules.payments.core.entities.Payment;
import reiz.miniecommerce.modules.payments.core.interfaces.repositories.PaymentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * Periodically asks the gateway about charges that were opened and never settled.
 *
 * <p>The webhook is the fast path and this is the safety net. Between them sits a real gap:
 * the webhook answers 200 immediately and settles in the background, so a failure there is
 * never retried by the gateway, and a customer who paid would be left with a PENDING order.
 * Nothing in the system would flag it — the money simply arrived and the order never moved.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class PaymentReconciliationJob {

    private final PaymentRepository paymentRepository;
    private final PaymentService paymentService;
    private final MercadoPagoProperties properties;

    @Scheduled(
            fixedDelayString = "${app.mercado-pago.reconciliation-interval:5m}",
            initialDelayString = "${app.mercado-pago.reconciliation-interval:5m}")
    public void run() {
        OffsetDateTime now = OffsetDateTime.now();
        sweep(now.minus(properties.getReconciliationMaxAge()),
              now.minus(properties.getReconciliationMinAge()));
    }

    /**
     * Takes the window explicitly so the sweep can be exercised without waiting for rows to
     * age.
     *
     * @return how many charges this sweep settled
     */
    public int sweep(OffsetDateTime from, OffsetDateTime to) {
        List<Payment> pending = paymentRepository.findUnpaidOpenedBetween(from, to);
        if (pending.isEmpty()) {
            return 0;
        }

        int settled = 0;
        for (Payment payment : pending.stream().limit(properties.getReconciliationBatchSize()).toList()) {
            try {
                if (paymentService.reconcile(payment.getId())) {
                    settled++;
                }
            } catch (RuntimeException e) {
                // one unreachable charge must not stop the rest of the sweep
                log.error("Reconciliation failed for payment {}", payment.getId(), e);
            }
        }

        if (settled > 0) {
            log.warn("Reconciliation settled {} of {} unsettled charges", settled, pending.size());
        }
        return settled;
    }
}
