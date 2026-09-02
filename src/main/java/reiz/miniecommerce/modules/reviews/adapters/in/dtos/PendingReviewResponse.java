package reiz.miniecommerce.modules.reviews.adapters.in.dtos;

import reiz.miniecommerce.modules.orders.core.entities.OrderItem;

import java.util.UUID;

/** A delivered item still waiting for its rating. */
public record PendingReviewResponse(UUID orderItemId, UUID orderId, UUID productId, Integer quantity) {

    public static PendingReviewResponse from(OrderItem item) {
        return new PendingReviewResponse(item.getId(), item.getOrderId(),
                item.getProductId(), item.getQuantity());
    }
}
