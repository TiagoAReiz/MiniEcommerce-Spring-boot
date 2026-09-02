package reiz.miniecommerce.modules.address.adapters.out.repositories;

import reiz.miniecommerce.modules.address.adapters.out.repositories.entities.AddressJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface AddressJpaRepository extends JpaRepository<AddressJpaEntity, UUID> {

    List<AddressJpaEntity> findByUserId(UUID userId);

    Optional<AddressJpaEntity> findByUserIdAndPrimaryTrue(UUID userId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update AddressJpaEntity a set a.primary = false where a.user.id = :userId and a.primary = true")
    void clearPrimaryFor(@Param("userId") UUID userId);
}
