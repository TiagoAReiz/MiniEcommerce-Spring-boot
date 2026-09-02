package reiz.miniecommerce.modules.address.adapters.out.repositories;

import reiz.miniecommerce.modules.address.adapters.mappers.AddressJpaMapper;
import reiz.miniecommerce.modules.address.core.entities.Address;
import reiz.miniecommerce.modules.address.core.interfaces.repositories.AddressRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Driven adapter: fulfils the {@link AddressRepository} port with Spring Data JPA.
 */
@Component
@RequiredArgsConstructor
public class AddressRepositoryAdapter implements AddressRepository {

    private final AddressJpaRepository jpaRepository;
    private final AddressJpaMapper mapper;

    @Override
    public Address save(Address address) {
        return mapper.toDomain(jpaRepository.save(mapper.toEntity(address)));
    }

    @Override
    public Optional<Address> findById(UUID id) {
        return jpaRepository.findById(id).map(mapper::toDomain);
    }

    @Override
    public List<Address> findByUserId(UUID userId) {
        return jpaRepository.findByUserId(userId).stream().map(mapper::toDomain).toList();
    }

    @Override
    public Optional<Address> findPrimaryByUserId(UUID userId) {
        return jpaRepository.findByUserIdAndPrimaryTrue(userId).map(mapper::toDomain);
    }

    @Override
    public void clearPrimaryFor(UUID userId) {
        jpaRepository.clearPrimaryFor(userId);
    }

    @Override
    public void deleteById(UUID id) {
        jpaRepository.deleteById(id);
    }
}
