package reiz.miniecommerce.modules.users.adapters.mappers;

import reiz.miniecommerce.modules.users.adapters.out.repositories.entities.UserJpaEntity;
import reiz.miniecommerce.modules.users.core.entities.User;
import org.springframework.stereotype.Component;

/**
 * Translates between the {@link UserJpaEntity} persistence entity and the {@link User} domain model.
 */
@Component
public class UserJpaMapper {

    public User toDomain(UserJpaEntity entity) {
        if (entity == null) {
            return null;
        }
        return User.builder()
                .id(entity.getId())
                .googleSub(entity.getGoogleSub())
                .name(entity.getName())
                .email(entity.getEmail())
                .cpf(entity.getCpf())
                .phone(entity.getPhone())
                .photoUrl(entity.getPhotoUrl())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }

    public UserJpaEntity toEntity(User domain) {
        return UserJpaEntity.builder()
                .id(domain.getId())
                .googleSub(domain.getGoogleSub())
                .name(domain.getName())
                .email(domain.getEmail())
                .cpf(domain.getCpf())
                .phone(domain.getPhone())
                .photoUrl(domain.getPhotoUrl())
                .build();
    }
}
