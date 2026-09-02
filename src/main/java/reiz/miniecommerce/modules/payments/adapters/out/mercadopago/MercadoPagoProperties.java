package reiz.miniecommerce.modules.payments.adapters.out.mercadopago;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@Getter
@Setter
@ConfigurationProperties(prefix = "app.mercado-pago")
public class MercadoPagoProperties {

    private String baseUrl = "https://api.mercadopago.com";

    /** Bearer token for the API. Server side only; never reaches the browser. */
    private String accessToken;

    /** Secret from Webhooks > Configure notification, used to verify inbound signatures. */
    private String webhookSecret;

    /** Where Mercado Pago should post notifications. Needs to be publicly reachable. */
    private String notificationUrl;

    /** Where the customer lands after paying. */
    private String successUrl = "http://localhost:3000/orders";
    private String failureUrl = "http://localhost:3000/orders";
    private String pendingUrl = "http://localhost:3000/orders";

    /** Notifications are remembered this long, so a retry is recognised as a repeat. */
    private Duration notificationMemory = Duration.ofHours(24);

    private Duration timeout = Duration.ofSeconds(10);

    /** How often unsettled charges are checked against the gateway. */
    private Duration reconciliationInterval = Duration.ofMinutes(5);

    /** A charge is only reconciled once it is at least this old. */
    private Duration reconciliationMinAge = Duration.ofMinutes(5);

    /** Past this age a charge is treated as abandoned and stops being polled. */
    private Duration reconciliationMaxAge = Duration.ofHours(24);

    /** Ceiling per run, so a backlog cannot turn into a burst of gateway calls. */
    private int reconciliationBatchSize = 50;
}
