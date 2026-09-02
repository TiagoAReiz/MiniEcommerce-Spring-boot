package reiz.miniecommerce.modules.orders.adapters.out.repositories;

import reiz.miniecommerce.modules.orders.adapters.out.repositories.entities.OrderItemJpaEntity;
import reiz.miniecommerce.modules.orders.core.entities.OrderStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface OrderItemJpaRepository extends JpaRepository<OrderItemJpaEntity, UUID> {

    List<OrderItemJpaEntity> findByOrderId(UUID orderId);

    /**
     * Items of orders in the given status that the customer has not reviewed yet.
     * Used to trigger the "rate your purchase" notification.
     */
    @Query("""
            select oi
            from OrderItemJpaEntity oi
            where oi.order.status = :status
              and oi.order.user.id = :userId
              and not exists (
                  select 1
                  from ReviewJpaEntity r
                  where r.orderItem = oi
              )
            """)
    List<OrderItemJpaEntity> findAwaitingReview(@Param("status") OrderStatus status,
                                                @Param("userId") UUID userId);
}
