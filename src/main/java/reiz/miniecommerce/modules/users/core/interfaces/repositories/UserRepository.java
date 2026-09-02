package reiz.miniecommerce.modules.users.core.interfaces.repositories;

import reiz.miniecommerce.modules.users.core.entities.User;


import java.util.Optional;
import java.util.UUID;

/**
 * Output port for User persistence. The core owns this contract; adapters implement it.
 */
public interface UserRepository {

    User save(User user);

    Optional<User> findById(UUID id);

    Optional<User> findByGoogleSub(String googleSub);

    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);

    boolean existsByCpf(String cpf);

    void deleteById(UUID id);
}
