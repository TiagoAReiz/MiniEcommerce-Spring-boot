package reiz.miniecommerce.modules.payments.core.entities;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Domain model for Payment. Free of persistence concerns: no JPA annotations,
 * no framework types. Other aggregates are referenced by id.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(of = "id")
public class Payment {

    private UUID id;
    private BigDecimal amount;
    private boolean paid;
    private String mercadoPagoId;

    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;

    public void markAsPaid(String mercadoPagoId) {
        this.mercadoPagoId = mercadoPagoId;
        this.paid = true;
    }
}
