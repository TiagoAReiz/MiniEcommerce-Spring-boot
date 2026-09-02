package reiz.miniecommerce.modules.products.adapters.out.repositories;

import reiz.miniecommerce.modules.products.adapters.out.repositories.entities.ProductJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface ProductJpaRepository extends JpaRepository<ProductJpaEntity, UUID> {

    Page<ProductJpaEntity> findByNameContainingIgnoreCase(String name, Pageable pageable);

    /**
     * The {@code stock >= :quantity} in the WHERE clause is what makes this safe: a second
     * transaction re-evaluates it after the first commits, finds it false, and updates no row.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update ProductJpaEntity p
               set p.stock = p.stock - :quantity,
                   p.active = case when p.stock - :quantity = 0 then false else p.active end
             where p.id = :productId
               and p.active = true
               and p.stock >= :quantity
            """)
    int takeStock(@Param("productId") UUID productId, @Param("quantity") int quantity);
}
