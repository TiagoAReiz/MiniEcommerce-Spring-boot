package reiz.miniecommerce.modules.products.adapters.mappers;

import reiz.miniecommerce.modules.products.adapters.out.repositories.entities.ProductJpaEntity;
import reiz.miniecommerce.modules.products.adapters.out.repositories.entities.ProductPhotoJpaEntity;
import reiz.miniecommerce.modules.products.core.entities.ProductPhoto;
import org.springframework.stereotype.Component;

/**
 * Translates between the {@link ProductPhotoJpaEntity} persistence entity and the {@link ProductPhoto} domain model.
 */
@Component
public class ProductPhotoJpaMapper {

    public ProductPhoto toDomain(ProductPhotoJpaEntity entity) {
        if (entity == null) {
            return null;
        }
        return ProductPhoto.builder()
                .id(entity.getId())
                .productId(entity.getProduct() == null ? null : entity.getProduct().getId())
                .url(entity.getUrl())
                .position(entity.getPosition())
                .cover(entity.isCover())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }

    public ProductPhotoJpaEntity toEntity(ProductPhoto domain) {
        return ProductPhotoJpaEntity.builder()
                .id(domain.getId())
                .product(domain.getProductId() == null ? null
                        : ProductJpaEntity.builder().id(domain.getProductId()).build())
                .url(domain.getUrl())
                .position(domain.getPosition())
                .cover(domain.isCover())
                .build();
    }
}
