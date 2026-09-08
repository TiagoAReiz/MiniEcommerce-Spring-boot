package reiz.miniecommerce.modules.orders.core.entities;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Domain model for Order. Free of persistence concerns: no JPA annotations,
 * no framework types. Other aggregates are referenced by id.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(of = "id")
public class Order {

    private UUID id;
    private UUID userId;
    private UUID addressId;
    private UUID paymentId;
    private UUID shipmentId;
    private OrderStatus status;

    /**
     * Freight, frozen at checkout for the same reason line prices are frozen: it is derived
     * from a third-party lookup that can answer differently tomorrow, and the customer
     * agreed to this number.
     */
    private BigDecimal shippingCost;

    /**
     * The distance the freight was priced from, or null when it was not measured — the store
     * charges no freight, or the CEP lookup failed and the contingency rate applied. Kept so
     * a charge can be explained rather than merely asserted.
     */
    private BigDecimal shippingDistanceKm;

    /**
     * When the stock reservation of an unpaid order runs out.
     *
     * <p>Cleared once the order is paid. A CANCELLED order that still carries one was
     * abandoned rather than cancelled by hand — which is what makes this column a record of
     * lost sales and not just a timer.
     */
    private OffsetDateTime expiresAt;

    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;

    /**
     * What is actually owed: the goods plus the delivery.
     *
     * <p>Lives here so the amount charged and the amount displayed cannot drift apart —
     * the payment service and the API response both reach this one method.
     */
    public BigDecimal totalWith(BigDecimal itemsTotal) {
        BigDecimal items = itemsTotal == null ? BigDecimal.ZERO : itemsTotal;
        return items.add(shippingCost == null ? BigDecimal.ZERO : shippingCost);
    }
}
