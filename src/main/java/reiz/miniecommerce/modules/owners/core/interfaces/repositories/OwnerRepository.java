package reiz.miniecommerce.modules.owners.core.interfaces.repositories;

import reiz.miniecommerce.modules.owners.core.entities.Owner;


import java.util.Optional;
import java.util.UUID;

/**
 * Output port for Owner persistence. The core owns this contract; adapters implement it.
 */
public interface OwnerRepository {

    Owner save(Owner owner);

    Optional<Owner> findById(UUID id);

    Optional<Owner> findByGoogleSub(String googleSub);

    /**
     * Case-insensitive on purpose: the seeded row is typed by an operator while Google always
     * reports a lowercase address. An exact match would leave a seat with a capitalised e-mail
     * unclaimable forever, and silently — no error anywhere, the owner just never becomes one.
     */
    Optional<Owner> findByEmail(String email);

    /**
     * The store itself — the oldest owner row.
     *
     * <p>Products carry no owner, so there is exactly one storefront and one shipping origin
     * however many operators end up with a seat. This is the single place that assumption
     * lives: the day a product knows who sells it, freight looks up the origin from the
     * product instead and this method goes away.
     */
    Optional<Owner> findStore();

    void deleteById(UUID id);
}
