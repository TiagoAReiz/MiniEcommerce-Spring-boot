package reiz.miniecommerce.modules.payments.core.entities;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * A payment as the gateway reports it.
 *
 * <p>{@code externalReference} is the id we sent when opening the charge — our own payment
 * id. It is how a notification, which only carries the gateway's id, is tied back to a row
 * in this database.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GatewayPayment {

    private String gatewayId;
    private String externalReference;
    private String status;

    /** Mercado Pago reports a settled payment as {@code approved}. */
    public boolean isApproved() {
        return "approved".equalsIgnoreCase(status);
    }
}
