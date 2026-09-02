package reiz.miniecommerce.modules.reviews.core.exceptions;

/**
 * Reviews are public, so hiding their existence buys nothing: this answers 403, unlike the
 * private resources that answer 404.
 */
public class NotYourReviewException extends RuntimeException {

    public NotYourReviewException() {
        super("That review belongs to another customer");
    }
}
