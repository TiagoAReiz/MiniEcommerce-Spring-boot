package reiz.miniecommerce.modules.shipments.application.services;

import reiz.miniecommerce.modules.orders.application.services.OrderService;
import reiz.miniecommerce.modules.orders.core.entities.Order;
import reiz.miniecommerce.modules.orders.core.entities.OrderStatus;
import reiz.miniecommerce.modules.orders.core.interfaces.repositories.OrderRepository;
import reiz.miniecommerce.modules.shipments.core.entities.Shipment;
import reiz.miniecommerce.modules.shipments.core.exceptions.OrderNotPaidException;
import reiz.miniecommerce.modules.shipments.core.exceptions.ShipmentAlreadyExistsException;
import reiz.miniecommerce.modules.shipments.core.exceptions.ShipmentNotFoundException;
import reiz.miniecommerce.modules.shipments.core.interfaces.repositories.ShipmentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ShipmentService {

    private final ShipmentRepository shipmentRepository;
    private final OrderRepository orderRepository;
    private final OrderService orderService;
    @Transactional
    public Shipment createFor(UUID orderId, OffsetDateTime estimatedDeliveryAt) {
        Order order = orderService.visibleOrder(orderId, null, true);

        if (order.getStatus() != OrderStatus.PAID) {
            throw new OrderNotPaidException(orderId);
        }
        if (order.getShipmentId() != null) {
            throw new ShipmentAlreadyExistsException(orderId);
        }

        Shipment shipment = shipmentRepository.save(Shipment.builder()
                .addressId(order.getAddressId())
                .estimatedDeliveryAt(estimatedDeliveryAt)
                .build());

        order.setShipmentId(shipment.getId());
        orderRepository.save(order);
        return shipment;
    }

    @Transactional(readOnly = true)
    public Shipment forOrder(UUID orderId, UUID callerId, boolean asOwner) {
        Order order = orderService.visibleOrder(orderId, callerId, asOwner);

        if (order.getShipmentId() == null) {
            throw new ShipmentNotFoundException(orderId);
        }
        return shipmentRepository.findById(order.getShipmentId())
                .orElseThrow(() -> new ShipmentNotFoundException(order.getShipmentId()));
    }

    /** Marks the parcel as dispatched, which is also what moves the order to SHIPPED. */
    @Transactional
    public Shipment markShipped(UUID shipmentId, OffsetDateTime shippedAt) {
        Shipment shipment = shipmentRepository.findById(shipmentId)
                .orElseThrow(() -> new ShipmentNotFoundException(shipmentId));

        shipment.setShippedAt(shippedAt == null ? OffsetDateTime.now() : shippedAt);
        Shipment saved = shipmentRepository.save(shipment);

        orderRepository.findByShipmentId(shipmentId).ifPresent(order -> {
            if (order.getStatus().canTransitionTo(OrderStatus.SHIPPED)) {
                orderService.changeStatus(order.getId(), OrderStatus.SHIPPED);
            }
        });
        return saved;
    }
}
