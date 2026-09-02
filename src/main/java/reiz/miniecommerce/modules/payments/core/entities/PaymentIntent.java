package reiz.miniecommerce.modules.payments.core.entities;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * What the gateway hands back when a charge is opened: an identifier on their side and the
 * URL the customer has to be sent to.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PaymentIntent {

    private String gatewayReference;
    private String checkoutUrl;
}
