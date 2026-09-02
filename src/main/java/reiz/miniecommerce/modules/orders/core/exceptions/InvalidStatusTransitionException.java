package reiz.miniecommerce.modules.orders.core.exceptions;

import reiz.miniecommerce.modules.orders.core.entities.OrderStatus;

public class InvalidStatusTransitionException extends RuntimeException {

    public InvalidStatusTransitionException(OrderStatus from, OrderStatus to) {
        super("An order cannot go from " + from + " to " + to);
    }
}
