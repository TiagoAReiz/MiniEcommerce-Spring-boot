package reiz.miniecommerce.modules.shipments.core.entities;

import java.math.BigDecimal;

/**
 * What an order will be charged to ship, and why.
 *
 * <p>{@code distanceKm} is null when the price did not come from a measurement: the CEP
 * lookup failed and the contingency rate was applied. That null is the audit trail — see
 * V5__shipping.sql.
 *
 * <p>There is no zero-cost outcome. A store with no origin configured cannot quote at all
 * and refuses the checkout, so every quote that exists carries a real charge.
 */
public record ShippingQuote(BigDecimal cost, BigDecimal distanceKm) {

    /** The lookup failed; the order is priced at the contingency rate instead of blocked. */
    public static ShippingQuote fallback(BigDecimal cost) {
        return new ShippingQuote(cost, null);
    }

    public static ShippingQuote measured(BigDecimal cost, BigDecimal distanceKm) {
        return new ShippingQuote(cost, distanceKm);
    }

    /** True when the price is a flat contingency rather than a measured distance. */
    public boolean isEstimated() {
        return distanceKm == null;
    }
}
