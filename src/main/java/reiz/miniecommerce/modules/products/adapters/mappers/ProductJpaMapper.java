package reiz.miniecommerce.modules.products.adapters.mappers;

import reiz.miniecommerce.modules.products.adapters.out.repositories.entities.ProductJpaEntity;
import reiz.miniecommerce.modules.products.core.entities.Product;
import org.springframework.stereotype.Component;

import java.util.List;

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
                .category(entity.getCategory())
                .highlights(orEmpty(entity.getHighlights()))
                .specs(orEmpty(entity.getSpecs()))
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }

    /**
     * Every column the domain carries has to be written here. A save builds a detached entity
     * that Hibernate merges over the whole row, so a field left out of this builder is not
     * merely unchanged — it is erased. Editing a price would otherwise wipe the spec sheet.
     */
    public ProductJpaEntity toEntity(Product domain) {
        return ProductJpaEntity.builder()
                .id(domain.getId())
                .name(domain.getName())
                .description(domain.getDescription())
                .price(domain.getPrice())
                .stock(domain.getStock())
                .active(domain.isActive())
                .category(domain.getCategory())
                .highlights(orEmpty(domain.getHighlights()))
                .specs(orEmpty(domain.getSpecs()))
                .build();
    }

    /** The columns are NOT NULL with an empty-array default; nulls never reach the database. */
    private static <T> List<T> orEmpty(List<T> values) {
        return values == null ? List.of() : values;
    }
}
