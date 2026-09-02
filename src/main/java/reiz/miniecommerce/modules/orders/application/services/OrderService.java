package reiz.miniecommerce.modules.orders.application.services;

import reiz.miniecommerce.modules.orders.core.entities.Order;
import reiz.miniecommerce.modules.orders.core.entities.OrderItem;
import reiz.miniecommerce.modules.orders.core.entities.OrderStatus;
import reiz.miniecommerce.modules.orders.core.exceptions.InvalidStatusTransitionException;
import reiz.miniecommerce.modules.orders.core.exceptions.OrderNotFoundException;
import reiz.miniecommerce.modules.orders.core.interfaces.repositories.OrderItemRepository;
import reiz.miniecommerce.modules.orders.core.interfaces.repositories.OrderRepository;
import reiz.miniecommerce.modules.products.core.entities.Product;
import reiz.miniecommerce.modules.products.core.interfaces.repositories.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class OrderService {

    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final ProductRepository productRepository;

    @Transactional(readOnly = true)
    public Page<Order> ordersOf(UUID userId, Pageable pageable) {
        return orderRepository.findByUserId(userId, pageable);
    }

    /**
     * @param asOwner when true the caller sees any order; otherwise only their own, and
     *                someone else's order is reported as missing rather than forbidden
     */
    @Transactional(readOnly = true)
    public Order visibleOrder(UUID orderId, UUID callerId, boolean asOwner) {
        return orderRepository.findById(orderId)
                .filter(order -> asOwner || callerId.equals(order.getUserId()))
                .orElseThrow(() -> new OrderNotFoundException(orderId));
    }

    @Transactional(readOnly = true)
    public List<OrderItem> itemsOf(UUID orderId) {
        return orderItemRepository.findByOrderId(orderId);
    }

    /**
     * Moves an order along its lifecycle, refusing moves the state machine does not allow.
     *
     * <p>Cancelling returns the reserved stock, which also brings a product that sold out
     * back onto the shelf.
     */
    @Transactional
    public Order changeStatus(UUID orderId, OrderStatus target) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new OrderNotFoundException(orderId));

        if (!order.getStatus().canTransitionTo(target)) {
            throw new InvalidStatusTransitionException(order.getStatus(), target);
        }

        if (target == OrderStatus.CANCELLED) {
            restoreStock(orderId);
        }

        order.setStatus(target);
        return orderRepository.save(order);
    }

    private void restoreStock(UUID orderId) {
        for (OrderItem item : orderItemRepository.findByOrderId(orderId)) {
            productRepository.findById(item.getProductId()).ifPresent(product -> {
                product.restock(item.getQuantity());
                productRepository.save(product);
            });
        }
    }

    /** Sum of the frozen line prices — the amount actually owed for this order. */
    @Transactional(readOnly = true)
    public java.math.BigDecimal totalOf(UUID orderId) {
        return orderItemRepository.findByOrderId(orderId).stream()
                .map(OrderItem::subtotal)
                .reduce(java.math.BigDecimal.ZERO, java.math.BigDecimal::add);
    }

    /** Products referenced by an order's items, for building the response. */
    @Transactional(readOnly = true)
    public List<Product> productsOf(List<OrderItem> items) {
        return items.stream()
                .map(item -> productRepository.findById(item.getProductId()))
                .flatMap(java.util.Optional::stream)
                .toList();
    }
}
