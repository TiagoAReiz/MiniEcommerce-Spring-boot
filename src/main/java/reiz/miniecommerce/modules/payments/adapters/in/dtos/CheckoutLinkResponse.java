package reiz.miniecommerce.modules.payments.adapters.in.dtos;

import reiz.miniecommerce.modules.payments.core.entities.PaymentIntent;

import java.math.BigDecimal;

/**
 * Where to send the customer to pay. The front end redirects to {@code checkoutUrl}.
 */
public record CheckoutLinkResponse(String checkoutUrl, String gatewayReference, BigDecimal amount) {

    public static CheckoutLinkResponse of(PaymentIntent intent, BigDecimal amount) {
        return new CheckoutLinkResponse(intent.getCheckoutUrl(), intent.getGatewayReference(), amount);
    }
}
