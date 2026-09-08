package reiz.miniecommerce.modules.orders.adapters.mappers;

import reiz.miniecommerce.modules.address.adapters.out.repositories.entities.AddressJpaEntity;
import reiz.miniecommerce.modules.orders.adapters.out.repositories.entities.OrderJpaEntity;
import reiz.miniecommerce.modules.orders.core.entities.Order;
import reiz.miniecommerce.modules.payments.adapters.out.repositories.entities.PaymentJpaEntity;
import reiz.miniecommerce.modules.shipments.adapters.out.repositories.entities.ShipmentJpaEntity;
import reiz.miniecommerce.modules.users.adapters.out.repositories.entities.UserJpaEntity;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/**
 * Translates between the {@link OrderJpaEntity} persistence entity and the {@link Order} domain model.
 */
@Component
public class OrderJpaMapper {

    public Order toDomain(OrderJpaEntity entity) {
        if (entity == null) {
            return null;
        }
        return Order.builder()
                .id(entity.getId())
                .userId(entity.getUser() == null ? null : entity.getUser().getId())
                .addressId(entity.getAddress() == null ? null : entity.getAddress().getId())
                .paymentId(entity.getPayment() == null ? null : entity.getPayment().getId())
                .shipmentId(entity.getShipment() == null ? null : entity.getShipment().getId())
                .status(entity.getStatus())
                .shippingCost(entity.getShippingCost())
                .shippingDistanceKm(entity.getShippingDistanceKm())
                .expiresAt(entity.getExpiresAt())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }

    public OrderJpaEntity toEntity(Order domain) {
        return OrderJpaEntity.builder()
                .id(domain.getId())
                .user(domain.getUserId() == null ? null
                        : UserJpaEntity.builder().id(domain.getUserId()).build())
                .address(domain.getAddressId() == null ? null
                        : AddressJpaEntity.builder().id(domain.getAddressId()).build())
                .payment(domain.getPaymentId() == null ? null
                        : PaymentJpaEntity.builder().id(domain.getPaymentId()).build())
                .shipment(domain.getShipmentId() == null ? null
                        : ShipmentJpaEntity.builder().id(domain.getShipmentId()).build())
                .status(domain.getStatus())
                // Not optional: a save merges the whole row, so leaving freight out here
                // would zero a quote the customer already agreed to every time an order is
                // touched to attach a payment or a shipment.
                .shippingCost(domain.getShippingCost() == null ? BigDecimal.ZERO : domain.getShippingCost())
                .shippingDistanceKm(domain.getShippingDistanceKm())
                .expiresAt(domain.getExpiresAt())
                .build();
    }
}
