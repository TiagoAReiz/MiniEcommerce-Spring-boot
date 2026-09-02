package reiz.miniecommerce.modules.products.application.services;

import reiz.miniecommerce.modules.products.core.entities.Product;
import reiz.miniecommerce.modules.products.core.exceptions.ProductNotFoundException;
import reiz.miniecommerce.modules.products.core.interfaces.repositories.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ProductService {

    private final ProductRepository productRepository;

    @Transactional(readOnly = true)
    public Page<Product> search(String query, Pageable pageable) {
        return productRepository.searchByName(query == null ? "" : query, pageable);
    }

    @Transactional(readOnly = true)
    public Product byId(UUID id) {
        return productRepository.findById(id).orElseThrow(() -> new ProductNotFoundException(id));
    }

    @Transactional
    public Product create(Product product) {
        product.setActive(true);
        return productRepository.save(product);
    }

    /**
     * @param active null leaves the current value alone. Treating an absent field as "true"
     *               would put a discontinued product back on sale just because someone fixed
     *               a typo in its name.
     */
    @Transactional
    public Product update(UUID id, Product changes, Boolean active) {
        Product existing = byId(id);

        existing.setName(changes.getName());
        existing.setDescription(changes.getDescription());
        existing.setPrice(changes.getPrice());
        existing.setStock(changes.getStock());
        if (active != null) {
            existing.setActive(active);
        }
        return productRepository.save(existing);
    }

    @Transactional
    public void deactivate(UUID id) {
        Product product = byId(id);
        product.setActive(false);
        productRepository.save(product);
    }
}
