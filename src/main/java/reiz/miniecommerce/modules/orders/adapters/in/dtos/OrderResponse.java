package reiz.miniecommerce.modules.orders.adapters.in.dtos;

import reiz.miniecommerce.modules.orders.core.entities.Order;
import reiz.miniecommerce.modules.orders.core.entities.OrderStatus;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * {@code total} is what the customer owes and what the payment gateway will charge: goods
 * plus delivery. {@code itemsTotal} is the goods alone, so a front end can show the two
 * lines without adding or subtracting anything itself.
 *
 * <p>{@code shippingDistanceKm} is null when the freight was not measured — either the store
 * charges none, or the postal-code lookup failed and a flat contingency rate was applied.
 */
public record OrderResponse(
        UUID id,
        OrderStatus status,
        BigDecimal total,
        BigDecimal itemsTotal,
        BigDecimal shippingCost,
        BigDecimal shippingDistanceKm,
        int itemCount,
        UUID addressId,
        UUID paymentId,
        UUID shipmentId,
        List<OrderItemResponse> items,
        OffsetDateTime createdAt,
        OffsetDateTime expiresAt) {

    public static OrderResponse of(Order order, List<OrderItemResponse> items, BigDecimal itemsTotal) {
        int count = items.stream().mapToInt(OrderItemResponse::quantity).sum();
        BigDecimal shipping = order.getShippingCost() == null ? BigDecimal.ZERO : order.getShippingCost();

        return new OrderResponse(order.getId(), order.getStatus(),
                order.totalWith(itemsTotal), itemsTotal, shipping, order.getShippingDistanceKm(),
                count, order.getAddressId(), order.getPaymentId(), order.getShipmentId(),
                items, order.getCreatedAt(), order.getExpiresAt());
    }
}
