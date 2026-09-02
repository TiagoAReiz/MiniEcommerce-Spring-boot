package reiz.miniecommerce.modules.orders.core.exceptions;

/** The account still lacks the data an order needs: CPF and phone are not in a Google token. */
public class CheckoutBlockedException extends RuntimeException {

    public CheckoutBlockedException() {
        super("Profile is missing the CPF or phone required to place an order");
    }
}
