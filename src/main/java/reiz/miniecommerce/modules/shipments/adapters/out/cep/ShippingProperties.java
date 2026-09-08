package reiz.miniecommerce.modules.shipments.adapters.out.cep;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.math.BigDecimal;
import java.time.Duration;

@Getter
@Setter
@ConfigurationProperties(prefix = "app.shipping")
public class ShippingProperties {

    /** Charged per kilometre of estimated road distance. */
    private BigDecimal pricePerKm = new BigDecimal("1.00");

    /**
     * Multiplier from straight-line distance to road distance.
     *
     * <p>A CEP lookup gives coordinates, not routes, and the geodesic between two points
     * undershoots the drive by roughly a third in Brazil. This is that correction, kept
     * configurable because it is an approximation and not a fact.
     */
    private BigDecimal roadFactor = new BigDecimal("1.3");

    /**
     * Charged when the store has an origin but the lookup could not produce a distance.
     *
     * <p>A flat rate is wrong for everybody by design — the same amount for a customer three
     * kilometres away and one eight hundred away. It is the price of not blocking the sale,
     * and orders that paid it are the ones with a null shipping_distance_km.
     */
    private BigDecimal fallbackCost = new BigDecimal("25.00");

    private String cepBaseUrl = "https://brasilapi.com.br";

    /**
     * Deliberately short: this runs while the customer waits on the checkout button, and the
     * contingency rate is already the answer for a provider that is slow or down.
     *
     * <p>Applied to the connect and the read phase separately, and a quote resolves up to two
     * postal codes. The wait is bounded at one timeout in the case that matters — a provider
     * that is down fails the origin first and the quote gives up there without asking about
     * the destination. Two are only possible when the first lookup succeeds slowly and the
     * second then fails, which no outage produces.
     */
    private Duration timeout = Duration.ofSeconds(3);
}
