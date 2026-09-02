package reiz.miniecommerce.modules.reviews.core.exceptions;

/** Only what actually arrived can be rated. */
public class OrderNotDeliveredException extends RuntimeException {

    public OrderNotDeliveredException() {
        super("The order has not been delivered yet");
    }
}
