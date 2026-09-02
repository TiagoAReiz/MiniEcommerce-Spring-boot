package reiz.miniecommerce.modules.orders.core.exceptions;

import java.util.UUID;

/** The delivery address does not exist, or is not this customer's. */
public class AddressNotOwnedException extends RuntimeException {

    public AddressNotOwnedException(UUID addressId) {
        super("No address with id " + addressId + " for the current user");
    }
}
