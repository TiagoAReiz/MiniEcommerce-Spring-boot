package reiz.miniecommerce.modules.reviews.adapters.mappers;

import reiz.miniecommerce.modules.orders.adapters.out.repositories.entities.OrderItemJpaEntity;
import reiz.miniecommerce.modules.products.adapters.out.repositories.entities.ProductJpaEntity;
import reiz.miniecommerce.modules.reviews.adapters.out.repositories.entities.ReviewJpaEntity;
import reiz.miniecommerce.modules.reviews.core.entities.Review;
import org.springframework.stereotype.Component;

/**
 * Translates between the {@link ReviewJpaEntity} persistence entity and the {@link Review} domain model.
 */
@Component
public class ReviewJpaMapper {

    public Review toDomain(ReviewJpaEntity entity) {
        if (entity == null) {
            return null;
        }
        return Review.builder()
                .id(entity.getId())
                .orderItemId(entity.getOrderItem() == null ? null : entity.getOrderItem().getId())
                .productId(entity.getProduct() == null ? null : entity.getProduct().getId())
                .title(entity.getTitle())
                .comment(entity.getComment())
                .rating(entity.getRating())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }

    public ReviewJpaEntity toEntity(Review domain) {
        return ReviewJpaEntity.builder()
                .id(domain.getId())
                .orderItem(domain.getOrderItemId() == null ? null
                        : OrderItemJpaEntity.builder().id(domain.getOrderItemId()).build())
                .product(domain.getProductId() == null ? null
                        : ProductJpaEntity.builder().id(domain.getProductId()).build())
                .title(domain.getTitle())
                .comment(domain.getComment())
                .rating(domain.getRating())
                .build();
    }
}
