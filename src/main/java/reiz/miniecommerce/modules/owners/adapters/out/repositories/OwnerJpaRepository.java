package reiz.miniecommerce.modules.owners.adapters.out.repositories;

import reiz.miniecommerce.modules.owners.adapters.out.repositories.entities.OwnerJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface OwnerJpaRepository extends JpaRepository<OwnerJpaEntity, UUID> {

    Optional<OwnerJpaEntity> findByGoogleSub(String googleSub);

    Optional<OwnerJpaEntity> findByEmailIgnoreCase(String email);
}
