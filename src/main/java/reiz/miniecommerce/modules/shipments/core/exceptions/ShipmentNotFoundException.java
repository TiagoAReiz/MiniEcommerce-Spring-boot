package reiz.miniecommerce.modules.shipments.core.exceptions;

import java.util.UUID;

public class ShipmentNotFoundException extends RuntimeException {

    public ShipmentNotFoundException(UUID id) {
        super("No shipment " + id + " visible to the caller");
    }
}
