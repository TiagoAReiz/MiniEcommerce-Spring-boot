package reiz.miniecommerce.modules.shipments.core.exceptions;

import java.util.UUID;

public class ShipmentAlreadyExistsException extends RuntimeException {

    public ShipmentAlreadyExistsException(UUID orderId) {
        super("Order " + orderId + " already has a shipment");
    }
}
