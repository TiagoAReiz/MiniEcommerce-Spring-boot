package reiz.miniecommerce.modules.orders.adapters.out.repositories;

import reiz.miniecommerce.modules.orders.adapters.mappers.OrderItemJpaMapper;
import reiz.miniecommerce.modules.orders.core.entities.OrderItem;
import reiz.miniecommerce.modules.orders.core.entities.OrderStatus;
import reiz.miniecommerce.modules.orders.core.interfaces.repositories.OrderItemRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Driven adapter: fulfils the {@link OrderItemRepository} port with Spring Data JPA.
 */
@Component
@RequiredArgsConstructor
public class OrderItemRepositoryAdapter implements OrderItemRepository {

    private final OrderItemJpaRepository jpaRepository;
    private final OrderItemJpaMapper mapper;

    @Override
    public OrderItem save(OrderItem orderItem) {
        return mapper.toDomain(jpaRepository.save(mapper.toEntity(orderItem)));
    }

    @Override
    public Optional<OrderItem> findById(UUID id) {
        return jpaRepository.findById(id).map(mapper::toDomain);
    }

    @Override
    public List<OrderItem> findByOrderId(UUID orderId) {
        return jpaRepository.findByOrderId(orderId).stream().map(mapper::toDomain).toList();
    }

    @Override
    public List<OrderItem> findAwaitingReview(OrderStatus orderStatus, UUID userId) {
        return jpaRepository.findAwaitingReview(orderStatus, userId).stream().map(mapper::toDomain).toList();
    }

    @Override
    public void deleteById(UUID id) {
        jpaRepository.deleteById(id);
    }
}
