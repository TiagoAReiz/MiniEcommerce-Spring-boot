package reiz.miniecommerce.modules.payments.adapters.mappers;

import reiz.miniecommerce.modules.payments.adapters.out.repositories.entities.PaymentJpaEntity;
import reiz.miniecommerce.modules.payments.core.entities.Payment;
import org.springframework.stereotype.Component;

/**
 * Translates between the {@link PaymentJpaEntity} persistence entity and the {@link Payment} domain model.
 */
@Component
public class PaymentJpaMapper {

    public Payment toDomain(PaymentJpaEntity entity) {
        if (entity == null) {
            return null;
        }
        return Payment.builder()
                .id(entity.getId())
                .amount(entity.getAmount())
                .paid(entity.isPaid())
                .mercadoPagoId(entity.getMercadoPagoId())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }

    public PaymentJpaEntity toEntity(Payment domain) {
        return PaymentJpaEntity.builder()
                .id(domain.getId())
                .amount(domain.getAmount())
                .paid(domain.isPaid())
                .mercadoPagoId(domain.getMercadoPagoId())
                .build();
    }
}
