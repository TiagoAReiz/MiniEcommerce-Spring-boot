package reiz.miniecommerce.modules.shipments.adapters.out.repositories;

import reiz.miniecommerce.modules.shipments.adapters.out.repositories.entities.ShipmentJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface ShipmentJpaRepository extends JpaRepository<ShipmentJpaEntity, UUID> {

    List<ShipmentJpaEntity> findByAddressId(UUID addressId);
}
