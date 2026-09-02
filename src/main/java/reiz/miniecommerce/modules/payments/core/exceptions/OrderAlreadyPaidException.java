package reiz.miniecommerce.modules.payments.core.exceptions;

import java.util.UUID;

public class OrderAlreadyPaidException extends RuntimeException {

    public OrderAlreadyPaidException(UUID orderId) {
        super("Order " + orderId + " is no longer awaiting payment");
    }
}
