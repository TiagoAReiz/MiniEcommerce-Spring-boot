package reiz.miniecommerce.modules.orders.adapters.in.dtos;

import reiz.miniecommerce.modules.orders.core.entities.Order;
import reiz.miniecommerce.modules.orders.core.entities.OrderStatus;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record OrderResponse(
        UUID id,
        OrderStatus status,
        BigDecimal total,
        int itemCount,
        UUID addressId,
        UUID paymentId,
        UUID shipmentId,
        List<OrderItemResponse> items,
        OffsetDateTime createdAt,
        OffsetDateTime expiresAt) {

    public static OrderResponse of(Order order, List<OrderItemResponse> items, BigDecimal total) {
        int count = items.stream().mapToInt(OrderItemResponse::quantity).sum();
        return new OrderResponse(order.getId(), order.getStatus(), total, count,
                order.getAddressId(), order.getPaymentId(), order.getShipmentId(),
                items, order.getCreatedAt(), order.getExpiresAt());
    }
}
