package reiz.miniecommerce.modules.orders.adapters.mappers;

import reiz.miniecommerce.modules.orders.adapters.out.repositories.entities.OrderItemJpaEntity;
import reiz.miniecommerce.modules.orders.adapters.out.repositories.entities.OrderJpaEntity;
import reiz.miniecommerce.modules.orders.core.entities.OrderItem;
import reiz.miniecommerce.modules.products.adapters.out.repositories.entities.ProductJpaEntity;
import org.springframework.stereotype.Component;

/**
 * Translates between the {@link OrderItemJpaEntity} persistence entity and the {@link OrderItem} domain model.
 */
@Component
public class OrderItemJpaMapper {

    public OrderItem toDomain(OrderItemJpaEntity entity) {
        if (entity == null) {
            return null;
        }
        return OrderItem.builder()
                .id(entity.getId())
                .orderId(entity.getOrder() == null ? null : entity.getOrder().getId())
                .productId(entity.getProduct() == null ? null : entity.getProduct().getId())
                .quantity(entity.getQuantity())
                .unitPrice(entity.getUnitPrice())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }

    public OrderItemJpaEntity toEntity(OrderItem domain) {
        return OrderItemJpaEntity.builder()
                .id(domain.getId())
                .order(domain.getOrderId() == null ? null
                        : OrderJpaEntity.builder().id(domain.getOrderId()).build())
                .product(domain.getProductId() == null ? null
                        : ProductJpaEntity.builder().id(domain.getProductId()).build())
                .quantity(domain.getQuantity())
                .unitPrice(domain.getUnitPrice())
                .build();
    }
}
