package reiz.miniecommerce.modules.shipments.adapters.mappers;

import reiz.miniecommerce.modules.address.adapters.out.repositories.entities.AddressJpaEntity;
import reiz.miniecommerce.modules.shipments.adapters.out.repositories.entities.ShipmentJpaEntity;
import reiz.miniecommerce.modules.shipments.core.entities.Shipment;
import org.springframework.stereotype.Component;

/**
 * Translates between the {@link ShipmentJpaEntity} persistence entity and the {@link Shipment} domain model.
 */
@Component
public class ShipmentJpaMapper {

    public Shipment toDomain(ShipmentJpaEntity entity) {
        if (entity == null) {
            return null;
        }
        return Shipment.builder()
                .id(entity.getId())
                .addressId(entity.getAddress() == null ? null : entity.getAddress().getId())
                .estimatedDeliveryAt(entity.getEstimatedDeliveryAt())
                .shippedAt(entity.getShippedAt())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }

    public ShipmentJpaEntity toEntity(Shipment domain) {
        return ShipmentJpaEntity.builder()
                .id(domain.getId())
                .address(domain.getAddressId() == null ? null
                        : AddressJpaEntity.builder().id(domain.getAddressId()).build())
                .estimatedDeliveryAt(domain.getEstimatedDeliveryAt())
                .shippedAt(domain.getShippedAt())
                .build();
    }
}
