package reiz.miniecommerce.modules.shipments.adapters.in.dtos;

import java.time.OffsetDateTime;

/**
 * No address here: it comes from the order the customer placed.
 */
public record CreateShipmentRequest(OffsetDateTime estimatedDeliveryAt) {
}
