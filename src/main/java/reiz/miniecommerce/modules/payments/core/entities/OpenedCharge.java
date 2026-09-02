package reiz.miniecommerce.modules.payments.core.entities;

/**
 * The result of opening a charge: the row that was created here and the redirect the
 * gateway handed back.
 */
public record OpenedCharge(Payment payment, PaymentIntent intent) {
}
