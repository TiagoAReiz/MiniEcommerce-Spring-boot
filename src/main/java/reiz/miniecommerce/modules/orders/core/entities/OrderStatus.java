package reiz.miniecommerce.modules.orders.core.entities;

import java.util.Set;

/**
 * The states an order moves through, and the moves that are allowed between them.
 *
 * <p>Mirrored by the {@code ck_orders_status} check constraint: the database refuses a value
 * outside this set, this enum refuses a move outside the graph.
 */
public enum OrderStatus {

    /** Created at checkout, stock already reserved, waiting for payment. */
    PENDING,

    /** Payment confirmed. Only the payment webhook may set this, never a manual call. */
    PAID,

    /** Handed to the carrier. Requires a shipment to exist. */
    SHIPPED,

    /** Terminal. Releases the customer's right to review the items. */
    DELIVERED,

    /** Terminal. Returns the reserved stock. */
    CANCELLED;

    private static final Set<OrderStatus> FROM_PENDING = Set.of(PAID, CANCELLED);
    private static final Set<OrderStatus> FROM_PAID = Set.of(SHIPPED, CANCELLED);
    private static final Set<OrderStatus> FROM_SHIPPED = Set.of(DELIVERED);

    public boolean canTransitionTo(OrderStatus target) {
        return switch (this) {
            case PENDING -> FROM_PENDING.contains(target);
            case PAID -> FROM_PAID.contains(target);
            case SHIPPED -> FROM_SHIPPED.contains(target);
            case DELIVERED, CANCELLED -> false;
        };
    }

    public boolean isTerminal() {
        return this == DELIVERED || this == CANCELLED;
    }

    /** Only a delivered order lets its items be reviewed. */
    public boolean allowsReview() {
        return this == DELIVERED;
    }
}
