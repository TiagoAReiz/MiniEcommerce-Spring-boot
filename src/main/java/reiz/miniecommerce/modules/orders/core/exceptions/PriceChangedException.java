package reiz.miniecommerce.modules.orders.core.exceptions;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * The product costs something different from what the customer put in the cart.
 *
 * <p>Checkout refuses rather than charging the new price silently: a cart can sit for days,
 * and nobody should be billed an amount they never agreed to.
 */
public class PriceChangedException extends RuntimeException {

    public PriceChangedException(UUID productId, BigDecimal cartPrice, BigDecimal currentPrice) {
        super("Product " + productId + " was " + cartPrice + " in the cart and now costs " + currentPrice);
    }
}
