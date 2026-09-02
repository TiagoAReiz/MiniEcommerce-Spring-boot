package reiz.miniecommerce.modules.reviews.adapters.out.repositories;

import reiz.miniecommerce.modules.reviews.adapters.mappers.ReviewJpaMapper;
import reiz.miniecommerce.modules.reviews.core.entities.Review;
import reiz.miniecommerce.modules.reviews.core.interfaces.repositories.ReviewRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

/**
 * Driven adapter: fulfils the {@link ReviewRepository} port with Spring Data JPA.
 */
@Component
@RequiredArgsConstructor
public class ReviewRepositoryAdapter implements ReviewRepository {

    private final ReviewJpaRepository jpaRepository;
    private final ReviewJpaMapper mapper;

    @Override
    public Review save(Review review) {
        return mapper.toDomain(jpaRepository.save(mapper.toEntity(review)));
    }

    @Override
    public Optional<Review> findById(UUID id) {
        return jpaRepository.findById(id).map(mapper::toDomain);
    }

    @Override
    public Page<Review> findByProductId(UUID productId, Pageable pageable) {
        return jpaRepository.findByProductId(productId, pageable).map(mapper::toDomain);
    }

    @Override
    public boolean existsByOrderItemId(UUID orderItemId) {
        return jpaRepository.existsByOrderItemId(orderItemId);
    }

    @Override
    public double averageRatingFor(UUID productId) {
        return jpaRepository.averageRatingFor(productId);
    }

    @Override
    public long countByProductId(UUID productId) {
        return jpaRepository.countByProductId(productId);
    }

    @Override
    public void deleteById(UUID id) {
        jpaRepository.deleteById(id);
    }
}
