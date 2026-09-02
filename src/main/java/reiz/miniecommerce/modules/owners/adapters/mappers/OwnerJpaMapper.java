package reiz.miniecommerce.modules.owners.adapters.mappers;

import reiz.miniecommerce.modules.owners.adapters.out.repositories.entities.OwnerJpaEntity;
import reiz.miniecommerce.modules.owners.core.entities.Owner;
import org.springframework.stereotype.Component;

/**
 * Translates between the {@link OwnerJpaEntity} persistence entity and the {@link Owner} domain model.
 */
@Component
public class OwnerJpaMapper {

    public Owner toDomain(OwnerJpaEntity entity) {
        if (entity == null) {
            return null;
        }
        return Owner.builder()
                .id(entity.getId())
                .googleSub(entity.getGoogleSub())
                .email(entity.getEmail())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }

    public OwnerJpaEntity toEntity(Owner domain) {
        return OwnerJpaEntity.builder()
                .id(domain.getId())
                .googleSub(domain.getGoogleSub())
                .email(domain.getEmail())
                .build();
    }
}
