package reiz.miniecommerce.modules.payments.adapters.in.dtos;

import reiz.miniecommerce.modules.payments.core.entities.Payment;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

public record PaymentResponse(
        UUID id,
        BigDecimal amount,
        boolean paid,
        String mercadoPagoId,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt) {

    public static PaymentResponse from(Payment payment) {
        return new PaymentResponse(payment.getId(), payment.getAmount(), payment.isPaid(),
                payment.getMercadoPagoId(), payment.getCreatedAt(), payment.getUpdatedAt());
    }
}
