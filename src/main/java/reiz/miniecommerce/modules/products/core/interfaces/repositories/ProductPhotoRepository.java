package reiz.miniecommerce.modules.products.core.interfaces.repositories;

import reiz.miniecommerce.modules.products.core.entities.ProductPhoto;

import java.util.Collection;
import java.util.List;

import java.util.Optional;
import java.util.UUID;

/**
 * Output port for ProductPhoto persistence. The core owns this contract; adapters implement it.
 */
public interface ProductPhotoRepository {

    ProductPhoto save(ProductPhoto productPhoto);

    Optional<ProductPhoto> findById(UUID id);

    List<ProductPhoto> findByProductId(UUID productId);

    /** Photos of several products at once, so listing a page costs one query, not one per row. */
    List<ProductPhoto> findByProductIds(Collection<UUID> productIds);

    Optional<ProductPhoto> findCoverByProductId(UUID productId);

    /**
     * Clears the cover flag from whatever photo currently holds it.
     *
     * <p>{@code uq_product_photos_cover} allows one cover per product, so promoting a new
     * one has to demote the old one in the same transaction.
     */
    void clearCoverFor(UUID productId);

    void deleteById(UUID id);
}
