package reiz.miniecommerce.modules.products.adapters.out.repositories;

import reiz.miniecommerce.modules.products.adapters.mappers.ProductJpaMapper;
import reiz.miniecommerce.modules.products.core.entities.Product;
import reiz.miniecommerce.modules.products.core.interfaces.repositories.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

/**
 * Driven adapter: fulfils the {@link ProductRepository} port with Spring Data JPA.
 */
@Component
@RequiredArgsConstructor
public class ProductRepositoryAdapter implements ProductRepository {

    private static final String CACHE = "products";

    private final ProductJpaRepository jpaRepository;
    private final ProductJpaMapper mapper;

    @Override
    @CacheEvict(cacheNames = CACHE, key = "#result.id")
    public Product save(Product product) {
        return mapper.toDomain(jpaRepository.save(mapper.toEntity(product)));
    }

    @Override
    // unless: a miss unwraps to null, and the cache is configured to reject nulls. Not
    // caching misses is also the right call — a product created a second later would keep
    // answering 404 until the entry expired.
    @Cacheable(cacheNames = CACHE, key = "#id", unless = "#result == null")
    public Optional<Product> findById(UUID id) {
        return jpaRepository.findById(id).map(mapper::toDomain);
    }

    // the conditional update bypasses save(), so the cached product would keep the old
    // stock until its TTL ran out
    @Override
    @CacheEvict(cacheNames = CACHE, key = "#productId")
    public boolean takeStock(UUID productId, int quantity) {
        return jpaRepository.takeStock(productId, quantity) == 1;
    }

    @Override
    public Page<Product> searchByName(String name, Pageable pageable) {
        return jpaRepository.findByNameContainingIgnoreCase(name, pageable).map(mapper::toDomain);
    }

    @Override
    @CacheEvict(cacheNames = CACHE, key = "#id")
    public void deleteById(UUID id) {
        jpaRepository.deleteById(id);
    }
}
