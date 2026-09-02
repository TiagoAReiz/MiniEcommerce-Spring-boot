package reiz.miniecommerce.modules.payments.core.exceptions;

/** The provider refused the request or could not be reached. */
public class PaymentGatewayException extends RuntimeException {

    public PaymentGatewayException(String message, Throwable cause) {
        super(message, cause);
    }
}
