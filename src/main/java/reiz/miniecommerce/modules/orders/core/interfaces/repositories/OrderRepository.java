package reiz.miniecommerce.modules.orders.core.interfaces.repositories;

import reiz.miniecommerce.modules.orders.core.entities.Order;
import reiz.miniecommerce.modules.orders.core.entities.OrderStatus;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.time.OffsetDateTime;
import java.util.List;

import java.util.Optional;
import java.util.UUID;

/**
 * Output port for Order persistence. The core owns this contract; adapters implement it.
 */
public interface OrderRepository {

    Order save(Order order);

    Optional<Order> findById(UUID id);

    Page<Order> findByUserId(UUID userId, Pageable pageable);

    List<Order> findByStatus(OrderStatus status);

    /** The order a payment belongs to, used when a gateway notification arrives. */
    Optional<Order> findByPaymentId(UUID paymentId);

    /** The order a shipment belongs to. */
    Optional<Order> findByShipmentId(UUID shipmentId);

    /** Unpaid orders whose stock reservation has run out. */
    List<Order> findExpired(OffsetDateTime now);

    void deleteById(UUID id);
}
