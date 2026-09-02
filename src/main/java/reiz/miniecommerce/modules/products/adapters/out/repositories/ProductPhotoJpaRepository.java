package reiz.miniecommerce.modules.products.adapters.out.repositories;

import reiz.miniecommerce.modules.products.adapters.out.repositories.entities.ProductPhotoJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ProductPhotoJpaRepository extends JpaRepository<ProductPhotoJpaEntity, UUID> {

    List<ProductPhotoJpaEntity> findByProductIdOrderByPositionAsc(UUID productId);

    List<ProductPhotoJpaEntity> findByProductIdInOrderByPositionAsc(Collection<UUID> productIds);

    Optional<ProductPhotoJpaEntity> findByProductIdAndCoverTrue(UUID productId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update ProductPhotoJpaEntity p set p.cover = false where p.product.id = :productId and p.cover = true")
    void clearCoverFor(@Param("productId") UUID productId);
}
