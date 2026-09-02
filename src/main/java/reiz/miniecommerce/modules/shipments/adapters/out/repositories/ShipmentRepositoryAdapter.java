package reiz.miniecommerce.modules.shipments.adapters.out.repositories;

import reiz.miniecommerce.modules.shipments.adapters.mappers.ShipmentJpaMapper;
import reiz.miniecommerce.modules.shipments.core.entities.Shipment;
import reiz.miniecommerce.modules.shipments.core.interfaces.repositories.ShipmentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Driven adapter: fulfils the {@link ShipmentRepository} port with Spring Data JPA.
 */
@Component
@RequiredArgsConstructor
public class ShipmentRepositoryAdapter implements ShipmentRepository {

    private final ShipmentJpaRepository jpaRepository;
    private final ShipmentJpaMapper mapper;

    @Override
    public Shipment save(Shipment shipment) {
        return mapper.toDomain(jpaRepository.save(mapper.toEntity(shipment)));
    }

    @Override
    public Optional<Shipment> findById(UUID id) {
        return jpaRepository.findById(id).map(mapper::toDomain);
    }

    @Override
    public List<Shipment> findByAddressId(UUID addressId) {
        return jpaRepository.findByAddressId(addressId).stream().map(mapper::toDomain).toList();
    }

    @Override
    public void deleteById(UUID id) {
        jpaRepository.deleteById(id);
    }
}
