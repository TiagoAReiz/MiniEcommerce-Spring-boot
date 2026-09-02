package reiz.miniecommerce.modules.products.adapters.out.repositories;

import reiz.miniecommerce.modules.products.adapters.mappers.ProductPhotoJpaMapper;
import reiz.miniecommerce.modules.products.core.entities.ProductPhoto;
import reiz.miniecommerce.modules.products.core.interfaces.repositories.ProductPhotoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Driven adapter: fulfils the {@link ProductPhotoRepository} port with Spring Data JPA.
 */
@Component
@RequiredArgsConstructor
public class ProductPhotoRepositoryAdapter implements ProductPhotoRepository {

    private final ProductPhotoJpaRepository jpaRepository;
    private final ProductPhotoJpaMapper mapper;

    @Override
    public ProductPhoto save(ProductPhoto productPhoto) {
        return mapper.toDomain(jpaRepository.save(mapper.toEntity(productPhoto)));
    }

    @Override
    public Optional<ProductPhoto> findById(UUID id) {
        return jpaRepository.findById(id).map(mapper::toDomain);
    }

    @Override
    public List<ProductPhoto> findByProductId(UUID productId) {
        return jpaRepository.findByProductIdOrderByPositionAsc(productId).stream().map(mapper::toDomain).toList();
    }

    @Override
    public List<ProductPhoto> findByProductIds(Collection<UUID> productIds) {
        if (productIds.isEmpty()) {
            return List.of();
        }
        return jpaRepository.findByProductIdInOrderByPositionAsc(productIds)
                .stream().map(mapper::toDomain).toList();
    }

    @Override
    public Optional<ProductPhoto> findCoverByProductId(UUID productId) {
        return jpaRepository.findByProductIdAndCoverTrue(productId).map(mapper::toDomain);
    }

    @Override
    public void clearCoverFor(UUID productId) {
        jpaRepository.clearCoverFor(productId);
    }

    @Override
    public void deleteById(UUID id) {
        jpaRepository.deleteById(id);
    }
}
