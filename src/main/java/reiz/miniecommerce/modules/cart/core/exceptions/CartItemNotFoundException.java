package reiz.miniecommerce.modules.cart.core.exceptions;

import java.util.UUID;

public class CartItemNotFoundException extends RuntimeException {

    public CartItemNotFoundException(UUID productId) {
        super("Product " + productId + " is not in the cart");
    }
}
