package reiz.miniecommerce.modules.payments.core.exceptions;

import java.util.UUID;

public class PaymentNotFoundException extends RuntimeException {

    public PaymentNotFoundException(UUID id) {
        super("No payment with id " + id + " visible to the caller");
    }
}
