package reiz.miniecommerce.modules.payments.adapters.out.repositories;

import reiz.miniecommerce.modules.payments.adapters.out.repositories.entities.PaymentJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PaymentJpaRepository extends JpaRepository<PaymentJpaEntity, UUID> {

    Optional<PaymentJpaEntity> findByMercadoPagoId(String mercadoPagoId);

    List<PaymentJpaEntity> findByPaidFalseAndCreatedAtBetweenOrderByCreatedAtDesc(
            OffsetDateTime from, OffsetDateTime to);
}
