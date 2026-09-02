package reiz.miniecommerce.modules.payments.core.interfaces.repositories;

import reiz.miniecommerce.modules.payments.core.entities.GatewayPayment;
import reiz.miniecommerce.modules.payments.core.entities.PaymentIntent;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

/**
 * Output port for the payment provider. The core knows there is a gateway; it does not know
 * it is Mercado Pago, nor that talking to it means HTTP.
 */
public interface PaymentGateway {

    /**
     * Opens a charge.
     *
     * @param paymentId our own payment id, sent along as the external reference so a later
     *                  notification can be traced back to this row
     */
    PaymentIntent openCharge(UUID paymentId, String description, BigDecimal amount, String payerEmail);

    /** Reads a payment back from the gateway. A notification is only a hint; this is the truth. */
    Optional<GatewayPayment> findPayment(String gatewayPaymentId);

    /**
     * Finds a payment by the reference we sent when opening the charge.
     *
     * <p>Needed because reconciliation starts from our side: a charge that was never settled
     * has no gateway id stored, so the only handle we have is the id we gave them.
     */
    Optional<GatewayPayment> findByExternalReference(UUID paymentId);
}
