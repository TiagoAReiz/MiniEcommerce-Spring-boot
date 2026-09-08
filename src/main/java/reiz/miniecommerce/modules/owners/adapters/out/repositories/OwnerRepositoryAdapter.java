package reiz.miniecommerce.modules.owners.adapters.out.repositories;

import reiz.miniecommerce.modules.owners.adapters.mappers.OwnerJpaMapper;
import reiz.miniecommerce.modules.owners.core.entities.Owner;
import reiz.miniecommerce.modules.owners.core.interfaces.repositories.OwnerRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

/**
 * Driven adapter: fulfils the {@link OwnerRepository} port with Spring Data JPA.
 */
@Component
@RequiredArgsConstructor
public class OwnerRepositoryAdapter implements OwnerRepository {

    private final OwnerJpaRepository jpaRepository;
    private final OwnerJpaMapper mapper;

    @Override
    public Owner save(Owner owner) {
        return mapper.toDomain(jpaRepository.save(mapper.toEntity(owner)));
    }

    @Override
    public Optional<Owner> findById(UUID id) {
        return jpaRepository.findById(id).map(mapper::toDomain);
    }

    @Override
    public Optional<Owner> findByGoogleSub(String googleSub) {
        return jpaRepository.findByGoogleSub(googleSub).map(mapper::toDomain);
    }

    @Override
    public Optional<Owner> findByEmail(String email) {
        return jpaRepository.findByEmailIgnoreCase(email).map(mapper::toDomain);
    }

    @Override
    public Optional<Owner> findStore() {
        return jpaRepository.findFirstByOrderByCreatedAtAsc().map(mapper::toDomain);
    }

    @Override
    public void deleteById(UUID id) {
        jpaRepository.deleteById(id);
    }
}
