package reiz.miniecommerce.modules.address.adapters.mappers;

import reiz.miniecommerce.modules.address.adapters.out.repositories.entities.AddressJpaEntity;
import reiz.miniecommerce.modules.address.core.entities.Address;
import reiz.miniecommerce.modules.users.adapters.out.repositories.entities.UserJpaEntity;
import org.springframework.stereotype.Component;

/**
 * Translates between the {@link AddressJpaEntity} persistence entity and the {@link Address} domain model.
 */
@Component
public class AddressJpaMapper {

    public Address toDomain(AddressJpaEntity entity) {
        if (entity == null) {
            return null;
        }
        return Address.builder()
                .id(entity.getId())
                .userId(entity.getUser() == null ? null : entity.getUser().getId())
                .primary(entity.isPrimary())
                .zipCode(entity.getZipCode())
                .street(entity.getStreet())
                .streetNumber(entity.getStreetNumber())
                .neighborhood(entity.getNeighborhood())
                .city(entity.getCity())
                .state(entity.getState())
                .country(entity.getCountry())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }

    public AddressJpaEntity toEntity(Address domain) {
        return AddressJpaEntity.builder()
                .id(domain.getId())
                .user(domain.getUserId() == null ? null
                        : UserJpaEntity.builder().id(domain.getUserId()).build())
                .primary(domain.isPrimary())
                .zipCode(domain.getZipCode())
                .street(domain.getStreet())
                .streetNumber(domain.getStreetNumber())
                .neighborhood(domain.getNeighborhood())
                .city(domain.getCity())
                .state(domain.getState())
                .country(domain.getCountry())
                .build();
    }
}
