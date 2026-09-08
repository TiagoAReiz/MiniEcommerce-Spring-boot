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
                .originZipCode(entity.getOriginZipCode())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }

    /**
     * Every column the domain carries has to be written here. A save builds a detached
     * entity that Hibernate merges over the whole row, so a field left out of this builder
     * is not merely unchanged — it is erased. That is how claiming the seat at first sign-in
     * would otherwise wipe the origin CEP the operator had configured.
     */
    public OwnerJpaEntity toEntity(Owner domain) {
        return OwnerJpaEntity.builder()
                .id(domain.getId())
                .googleSub(domain.getGoogleSub())
                .email(domain.getEmail())
                .originZipCode(domain.getOriginZipCode())
                .build();
    }
}
