package reiz.miniecommerce.modules.payments.core.interfaces.repositories;

import reiz.miniecommerce.modules.payments.core.entities.Payment;


import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Output port for Payment persistence. The core owns this contract; adapters implement it.
 */
public interface PaymentRepository {

    Payment save(Payment payment);

    Optional<Payment> findById(UUID id);

    Optional<Payment> findByMercadoPagoId(String mercadoPagoId);

    /**
     * Charges opened in the given window that were never settled.
     *
     * <p>The window has two ends on purpose. The near end skips charges too fresh to worry
     * about — the customer may still be typing their card. The far end skips the ones old
     * enough to be abandoned carts, which would otherwise be polled forever.
     *
     * <p>Newest first, because the batch is capped and abandoned charges are the ones that
     * accumulate. Oldest-first lets a day of abandoned carts fill the batch and starve the
     * charge that was actually paid minutes ago — the only one where a customer is waiting.
     */
    List<Payment> findUnpaidOpenedBetween(OffsetDateTime from, OffsetDateTime to);

    void deleteById(UUID id);
}
