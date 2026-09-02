package reiz.miniecommerce.modules.products.core.interfaces.repositories;

import reiz.miniecommerce.modules.products.core.entities.Product;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.Optional;
import java.util.UUID;

/**
 * Output port for Product persistence. The core owns this contract; adapters implement it.
 */
public interface ProductRepository {

    Product save(Product product);

    Optional<Product> findById(UUID id);

    Page<Product> searchByName(String name, Pageable pageable);

    /**
     * Takes stock in a single conditional statement, and reports whether it got it.
     *
     * <p>Reading the stock, checking it in Java and writing the new value back loses races:
     * two transactions both read the last unit, both find it sufficient, and both write the
     * same absolute result. Nothing looks wrong afterwards — the stock lands on a plausible
     * number while two orders exist for one item.
     *
     * <p>Letting the database do the arithmetic and the check together removes the window.
     * The product is also retired here when the shelf empties, so that decision stays
     * attached to the same statement.
     *
     * @return true when the stock was taken; false when there was not enough
     */
    boolean takeStock(UUID productId, int quantity);

    void deleteById(UUID id);
}
