package reiz.miniecommerce.modules.reviews.core.exceptions;

import java.util.UUID;

public class ReviewNotFoundException extends RuntimeException {

    public ReviewNotFoundException(UUID id) {
        super("No review with id " + id);
    }
}
