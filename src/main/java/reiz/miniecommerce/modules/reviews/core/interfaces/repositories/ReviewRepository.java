package reiz.miniecommerce.modules.reviews.core.interfaces.repositories;

import reiz.miniecommerce.modules.reviews.core.entities.Review;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.Optional;
import java.util.UUID;

/**
 * Output port for Review persistence. The core owns this contract; adapters implement it.
 */
public interface ReviewRepository {

    Review save(Review review);

    Optional<Review> findById(UUID id);

    Page<Review> findByProductId(UUID productId, Pageable pageable);

    boolean existsByOrderItemId(UUID orderItemId);

    /** Average rating over every review of a product, not just the page being shown. */
    double averageRatingFor(UUID productId);

    long countByProductId(UUID productId);

    void deleteById(UUID id);
}
