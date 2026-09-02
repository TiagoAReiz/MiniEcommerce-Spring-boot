package reiz.miniecommerce.modules.shipments.core.entities;

import java.time.OffsetDateTime;
import java.util.UUID;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Domain model for Shipment. Free of persistence concerns: no JPA annotations,
 * no framework types. Other aggregates are referenced by id.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(of = "id")
public class Shipment {

    private UUID id;
    private UUID addressId;
    private OffsetDateTime estimatedDeliveryAt;
    private OffsetDateTime shippedAt;

    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;

    public boolean isShipped() {
        return shippedAt != null;
    }
}
