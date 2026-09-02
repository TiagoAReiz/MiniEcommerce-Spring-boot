package reiz.miniecommerce.modules.orders.core.exceptions;

import java.util.UUID;

/**
 * Also thrown when the order belongs to someone else and the caller is not the store owner.
 * Answering 404 keeps a customer from probing which order ids exist.
 */
public class OrderNotFoundException extends RuntimeException {

    public OrderNotFoundException(UUID id) {
        super("No order with id " + id + " visible to the caller");
    }
}
