package reiz.miniecommerce.modules.auth.core.exceptions;

/**
 * Raised when a Google ID token fails verification: bad signature, wrong issuer,
 * wrong audience, or expired.
 */
public class InvalidGoogleTokenException extends RuntimeException {

    public InvalidGoogleTokenException(String message, Throwable cause) {
        super(message, cause);
    }
}
