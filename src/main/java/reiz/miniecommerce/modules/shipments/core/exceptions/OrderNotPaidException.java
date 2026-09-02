package reiz.miniecommerce.modules.shipments.core.exceptions;

import java.util.UUID;

/** Nothing is dispatched before the money is in. */
public class OrderNotPaidException extends RuntimeException {

    public OrderNotPaidException(UUID orderId) {
        super("Order " + orderId + " has not been paid");
    }
}
