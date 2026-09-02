package reiz.miniecommerce.modules.cart.adapters.in.dtos;

import reiz.miniecommerce.modules.cart.core.entities.CartLine;
import reiz.miniecommerce.modules.products.adapters.in.dtos.ProductResponse;

import java.math.BigDecimal;

/**
 * A cart line with the product it points at.
 *
 * <p>{@code unitPrice} is what the customer added the item at; {@code product.price} is what
 * it costs right now. They differ when the price changed while the cart sat idle, and the
 * front end can compare them to warn before checkout refuses.
 */
public record CartLineResponse(
        ProductResponse product,
        Integer quantity,
        BigDecimal unitPrice,
        BigDecimal subtotal) {

    public static CartLineResponse of(CartLine line, ProductResponse product) {
        return new CartLineResponse(product, line.getQuantity(), line.getUnitPrice(), line.subtotal());
    }
}
