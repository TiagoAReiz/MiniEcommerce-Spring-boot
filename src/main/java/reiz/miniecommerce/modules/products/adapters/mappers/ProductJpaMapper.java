package reiz.miniecommerce.modules.products.adapters.mappers;

import reiz.miniecommerce.modules.products.adapters.out.repositories.entities.ProductJpaEntity;
import reiz.miniecommerce.modules.products.core.entities.Product;
import org.springframework.stereotype.Component;

/**
 * Translates between the {@link ProductJpaEntity} persistence entity and the {@link Product} domain model.
 */
@Component
public class ProductJpaMapper {

    public Product toDomain(ProductJpaEntity entity) {
        if (entity == null) {
            return null;
        }
        return Product.builder()
                .id(entity.getId())
                .name(entity.getName())
                .description(entity.getDescription())
                .price(entity.getPrice())
                .stock(entity.getStock())
                .active(entity.isActive())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }

    public ProductJpaEntity toEntity(Product domain) {
        return ProductJpaEntity.builder()
                .id(domain.getId())
                .name(domain.getName())
                .description(domain.getDescription())
                .price(domain.getPrice())
                .stock(domain.getStock())
                .active(domain.isActive())
                .build();
    }
}
