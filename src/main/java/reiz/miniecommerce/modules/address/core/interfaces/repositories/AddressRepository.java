package reiz.miniecommerce.modules.address.core.interfaces.repositories;

import reiz.miniecommerce.modules.address.core.entities.Address;

import java.util.List;

import java.util.Optional;
import java.util.UUID;

/**
 * Output port for Address persistence. The core owns this contract; adapters implement it.
 */
public interface AddressRepository {

    Address save(Address address);

    Optional<Address> findById(UUID id);

    List<Address> findByUserId(UUID userId);

    Optional<Address> findPrimaryByUserId(UUID userId);

    /**
     * Clears the primary flag from whatever address currently holds it.
     *
     * <p>Exists because {@code uq_addresses_primary} allows exactly one primary address per
     * user: promoting a new one has to demote the old one in the same transaction, or the
     * insert hits the constraint.
     */
    void clearPrimaryFor(UUID userId);

    void deleteById(UUID id);
}
