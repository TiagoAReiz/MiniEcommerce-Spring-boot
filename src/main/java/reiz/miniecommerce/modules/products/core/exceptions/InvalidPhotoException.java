package reiz.miniecommerce.modules.products.core.exceptions;

/**
 * The uploaded file is not something this API will store: wrong type, empty, or too big.
 */
public class InvalidPhotoException extends RuntimeException {

    public InvalidPhotoException(String message) {
        super(message);
    }
}
