package reiz.miniecommerce.modules.payments.adapters.out.repositories;

import reiz.miniecommerce.modules.payments.adapters.mappers.PaymentJpaMapper;
import reiz.miniecommerce.modules.payments.core.entities.Payment;
import reiz.miniecommerce.modules.payments.core.interfaces.repositories.PaymentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Driven adapter: fulfils the {@link PaymentRepository} port with Spring Data JPA.
 */
@Component
@RequiredArgsConstructor
public class PaymentRepositoryAdapter implements PaymentRepository {

    private final PaymentJpaRepository jpaRepository;
    private final PaymentJpaMapper mapper;

    @Override
    public Payment save(Payment payment) {
        return mapper.toDomain(jpaRepository.save(mapper.toEntity(payment)));
    }

    @Override
    public Optional<Payment> findById(UUID id) {
        return jpaRepository.findById(id).map(mapper::toDomain);
    }

    @Override
    public Optional<Payment> findByMercadoPagoId(String mercadoPagoId) {
        return jpaRepository.findByMercadoPagoId(mercadoPagoId).map(mapper::toDomain);
    }

    @Override
    public List<Payment> findUnpaidOpenedBetween(OffsetDateTime from, OffsetDateTime to) {
        return jpaRepository.findByPaidFalseAndCreatedAtBetweenOrderByCreatedAtDesc(from, to)
                .stream().map(mapper::toDomain).toList();
    }

    @Override
    public void deleteById(UUID id) {
        jpaRepository.deleteById(id);
    }
}
