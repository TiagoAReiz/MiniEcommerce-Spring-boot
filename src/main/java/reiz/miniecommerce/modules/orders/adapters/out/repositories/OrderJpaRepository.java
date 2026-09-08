package reiz.miniecommerce.modules.orders.adapters.out.repositories;

import reiz.miniecommerce.modules.orders.adapters.out.repositories.entities.OrderJpaEntity;
import reiz.miniecommerce.modules.orders.core.entities.OrderStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface OrderJpaRepository extends JpaRepository<OrderJpaEntity, UUID> {

    Page<OrderJpaEntity> findByUserIdOrderByCreatedAtDesc(UUID userId, Pageable pageable);

    /** A loja inteira, para o painel do dono. */
    Page<OrderJpaEntity> findAllByOrderByCreatedAtDesc(Pageable pageable);

    List<OrderJpaEntity> findByStatus(OrderStatus status);

    Optional<OrderJpaEntity> findByPaymentId(UUID paymentId);

    Optional<OrderJpaEntity> findByShipmentId(UUID shipmentId);

    List<OrderJpaEntity> findByStatusAndExpiresAtLessThanOrderByExpiresAtAsc(
            OrderStatus status, OffsetDateTime now);
}
