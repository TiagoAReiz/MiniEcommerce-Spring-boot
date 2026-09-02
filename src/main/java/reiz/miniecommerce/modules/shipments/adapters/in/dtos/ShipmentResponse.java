package reiz.miniecommerce.modules.shipments.adapters.in.dtos;

import reiz.miniecommerce.modules.shipments.core.entities.Shipment;

import java.time.OffsetDateTime;
import java.util.UUID;

public record ShipmentResponse(
        UUID id,
        UUID addressId,
        OffsetDateTime estimatedDeliveryAt,
        OffsetDateTime shippedAt,
        boolean shipped,
        OffsetDateTime createdAt) {

    public static ShipmentResponse from(Shipment shipment) {
        return new ShipmentResponse(shipment.getId(), shipment.getAddressId(),
                shipment.getEstimatedDeliveryAt(), shipment.getShippedAt(),
                shipment.isShipped(), shipment.getCreatedAt());
    }
}
