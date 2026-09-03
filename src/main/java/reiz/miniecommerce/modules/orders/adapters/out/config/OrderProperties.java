package reiz.miniecommerce.modules.orders.adapters.out.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@Getter
@Setter
@ConfigurationProperties(prefix = "app.orders")
public class OrderProperties {

    /**
     * How long an unpaid order keeps its stock reserved.
     *
     * <p>Short enough that a browsing customer does not empty the shelf for everyone else,
     * long enough to survive a slow payment screen.
     */
    private Duration reservationWindow = Duration.ofMinutes(30);

    /**
     * How long an order keeps its stock once a charge is open.
     *
     * <p>Longer than the plain reservation on purpose: the customer is at the payment screen,
     * and an approved payment whose notification was lost is still recoverable while payment
     * reconciliation looks that far back. Keep it aligned with
     * {@code MP_RECONCILIATION_MAX_AGE}.
     */
    private Duration paymentWindow = Duration.ofHours(24);

    /** How often expired reservations are swept. */
    private Duration expirationInterval = Duration.ofMinutes(5);

    /** Ceiling per run, so a backlog cannot turn into one enormous transaction. */
    private int expirationBatchSize = 100;
}
