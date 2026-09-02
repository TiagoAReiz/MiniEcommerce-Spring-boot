package reiz.miniecommerce.modules.reviews.adapters.out.repositories;

import reiz.miniecommerce.modules.reviews.adapters.out.repositories.entities.ReviewJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface ReviewJpaRepository extends JpaRepository<ReviewJpaEntity, UUID> {

    Page<ReviewJpaEntity> findByProductId(UUID productId, Pageable pageable);

    boolean existsByOrderItemId(UUID orderItemId);

    @Query("select coalesce(avg(r.rating), 0) from ReviewJpaEntity r where r.product.id = :productId")
    double averageRatingFor(@Param("productId") UUID productId);

    long countByProductId(UUID productId);
}
