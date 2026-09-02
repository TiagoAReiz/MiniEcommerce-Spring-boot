package reiz.miniecommerce.modules.reviews.core.exceptions;

/**
 * One review per purchased item, enforced by the unique constraint on
 * {@code reviews.order_item_id}. Buying the same product again produces a new item, and
 * that one can be reviewed on its own.
 */
public class AlreadyReviewedException extends RuntimeException {

    public AlreadyReviewedException() {
        super("This item has already been reviewed");
    }
}
