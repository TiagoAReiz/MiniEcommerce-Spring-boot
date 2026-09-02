package reiz.miniecommerce.modules.users.core.exceptions;

import java.util.UUID;

public class UserNotFoundException extends RuntimeException {

    public UserNotFoundException(UUID id) {
        super("No user with id " + id);
    }
}
