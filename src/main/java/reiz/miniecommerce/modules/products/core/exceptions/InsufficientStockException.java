package reiz.miniecommerce.modules.products.core.exceptions;

import java.util.UUID;

/**
 * Asked for more units than the product has, or the product is no longer on sale.
 */
public class InsufficientStockException extends RuntimeException {

    public InsufficientStockException(UUID productId, int requested) {
        super("Product " + productId + " cannot supply " + requested + " units");
    }
}
