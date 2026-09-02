package reiz.miniecommerce.modules.orders.core.interfaces.repositories;

import reiz.miniecommerce.modules.orders.core.entities.OrderItem;
import reiz.miniecommerce.modules.orders.core.entities.OrderStatus;

import java.util.List;

import java.util.Optional;
import java.util.UUID;

/**
 * Output port for OrderItem persistence. The core owns this contract; adapters implement it.
 */
public interface OrderItemRepository {

    OrderItem save(OrderItem orderItem);

    Optional<OrderItem> findById(UUID id);

    List<OrderItem> findByOrderId(UUID orderId);

    /** Items of one customer, in the given order status, that they have not reviewed yet. */
    List<OrderItem> findAwaitingReview(OrderStatus orderStatus, UUID userId);

    void deleteById(UUID id);
}
