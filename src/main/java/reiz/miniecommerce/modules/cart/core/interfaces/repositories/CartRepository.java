package reiz.miniecommerce.modules.cart.core.interfaces.repositories;

import reiz.miniecommerce.modules.cart.core.entities.Cart;

import java.util.Optional;
import java.util.UUID;

/**
 * Output port for cart storage. The core states what it needs; the adapter
 * decides that "what" is Redis.
 */
public interface CartRepository {

    Cart save(Cart cart);

    Optional<Cart> findByUserId(UUID userId);

    void deleteByUserId(UUID userId);
}
