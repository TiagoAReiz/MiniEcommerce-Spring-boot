package reiz.miniecommerce.modules.orders.application.services;

import reiz.miniecommerce.modules.orders.adapters.out.config.OrderProperties;
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

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class OrderService {

    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final ProductRepository productRepository;
    private final OrderProperties properties;

    @Transactional(readOnly = true)
    public Page<Order> ordersOf(UUID userId, Pageable pageable) {
        return orderRepository.findByUserId(userId, pageable);
    }

    /**
     * Todos os pedidos da loja. Não recebe nem consulta o chamador de propósito: quem decide
     * se pode ver isto é a camada HTTP, com {@code @PreAuthorize}. Um filtro por papel aqui
     * dentro daria a impressão de que o método se protege sozinho, e a próxima rota que o
     * chamasse herdaria uma proteção que não existe.
     */
    @Transactional(readOnly = true)
    public Page<Order> allOrders(Pageable pageable) {
        return orderRepository.findAll(pageable);
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

        // Leaving PENDING by any deliberate route ends the reservation, so a later CANCELLED
        // order that still carries an expiry can only have been abandoned. That is what makes
        // the column readable as lost-sale history.
        order.setExpiresAt(null);
        order.setStatus(target);
        return orderRepository.save(order);
    }

    /**
     * Grants the longer reservation an order needs once a charge is open.
     *
     * <p>Without this the sweep could cancel and restock an order the customer is paying for
     * right now, or one already approved whose notification has not landed yet. The window
     * matches how far back payment reconciliation still looks: past it, nobody is coming.
     */
    @Transactional
    public void extendReservation(UUID orderId) {
        orderRepository.findById(orderId)
                .filter(order -> order.getStatus() == OrderStatus.PENDING)
                .ifPresent(order -> {
                    order.setExpiresAt(OffsetDateTime.now().plus(properties.getPaymentWindow()));
                    orderRepository.save(order);
                });
    }

    /**
     * Cancels an order whose reservation ran out and gives the stock back.
     *
     * <p>Deliberately not routed through {@link #changeStatus}: that clears {@code expiresAt},
     * and here the timestamp is exactly what has to survive — it is the evidence of why this
     * order was cancelled and when the sale was lost.
     */
    @Transactional
    public boolean expire(UUID orderId) {
        Order order = orderRepository.findById(orderId).orElse(null);
        if (order == null || order.getStatus() != OrderStatus.PENDING) {
            return false;
        }

        restoreStock(orderId);
        order.setStatus(OrderStatus.CANCELLED);
        orderRepository.save(order);
        return true;
    }

    private void restoreStock(UUID orderId) {
        for (OrderItem item : orderItemRepository.findByOrderId(orderId)) {
            productRepository.findById(item.getProductId()).ifPresent(product -> {
                product.restock(item.getQuantity());
                productRepository.save(product);
            });
        }
    }

    /**
     * The amount actually owed for this order: frozen line prices plus frozen freight.
     *
     * <p>This is what the payment gateway charges, so freight has to be part of it — an
     * order whose response shows a delivery fee and whose charge omits it would ship for
     * free and nobody would notice until the accounting did.
     */
    @Transactional(readOnly = true)
    public java.math.BigDecimal totalOf(UUID orderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new OrderNotFoundException(orderId));

        return order.totalWith(itemsTotalOf(orderId));
    }

    /** Goods only, before delivery. */
    @Transactional(readOnly = true)
    public java.math.BigDecimal itemsTotalOf(UUID orderId) {
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
