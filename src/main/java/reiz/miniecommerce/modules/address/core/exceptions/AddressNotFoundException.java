package reiz.miniecommerce.modules.address.core.exceptions;

import java.util.UUID;

/**
 * Also thrown when the address exists but belongs to someone else. Answering 404 rather
 * than 403 keeps a caller from confirming which ids exist.
 */
public class AddressNotFoundException extends RuntimeException {

    public AddressNotFoundException(UUID id) {
        super("No address with id " + id + " for the current user");
    }
}
