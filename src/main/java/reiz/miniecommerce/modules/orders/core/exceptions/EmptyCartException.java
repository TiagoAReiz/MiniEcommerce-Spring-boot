package reiz.miniecommerce.modules.orders.core.exceptions;

/** Checkout with nothing in the cart, or with a cart that expired in Redis. */
public class EmptyCartException extends RuntimeException {

    public EmptyCartException() {
        super("Cart is empty");
    }
}
