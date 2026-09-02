package reiz.miniecommerce.modules.orders.core.entities;

import java.time.OffsetDateTime;
import java.util.UUID;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Domain model for Order. Free of persistence concerns: no JPA annotations,
 * no framework types. Other aggregates are referenced by id.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(of = "id")
public class Order {

    private UUID id;
    private UUID userId;
    private UUID addressId;
    private UUID paymentId;
    private UUID shipmentId;
    private OrderStatus status;

    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
}
