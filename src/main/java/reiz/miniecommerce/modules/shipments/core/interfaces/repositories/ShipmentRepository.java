package reiz.miniecommerce.modules.shipments.core.interfaces.repositories;

import reiz.miniecommerce.modules.shipments.core.entities.Shipment;

import java.util.List;

import java.util.Optional;
import java.util.UUID;

/**
 * Output port for Shipment persistence. The core owns this contract; adapters implement it.
 */
public interface ShipmentRepository {

    Shipment save(Shipment shipment);

    Optional<Shipment> findById(UUID id);

    List<Shipment> findByAddressId(UUID addressId);

    void deleteById(UUID id);
}
