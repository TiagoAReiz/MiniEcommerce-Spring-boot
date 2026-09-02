package reiz.miniecommerce.modules.products;

import reiz.miniecommerce.modules.products.core.entities.Product;
import reiz.miniecommerce.modules.products.core.interfaces.repositories.ProductRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class ProductCacheTest {

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private StringRedisTemplate redisTemplate;

    private UUID productId;

    @AfterEach
    void cleanUp() {
        if (productId != null) {
            productRepository.deleteById(productId);
        }
    }

    @Test
    void cachesProductReadsInRedisAndEvictsOnWrite() {
        Product saved = productRepository.save(Product.builder()
                .name("Caneca")
                .price(new BigDecimal("39.90"))
                .stock(10)
                .build());
        productId = saved.getId();

        String cacheKey = "products::" + productId;
        assertThat(keyPresent(cacheKey)).isFalse();

        // first read hits Postgres and populates the cache
        Product first = productRepository.findById(productId).orElseThrow();
        assertThat(first.getName()).isEqualTo("Caneca");
        assertThat(awaitKey(cacheKey, true)).isTrue();

        // second read is served from Redis, deserialized back into the domain type
        Product second = productRepository.findById(productId).orElseThrow();
        assertThat(second.getName()).isEqualTo("Caneca");
        assertThat(second.getPrice()).isEqualByComparingTo("39.90");

        // writing evicts the entry
        saved.setName("Caneca nova");
        productRepository.save(saved);
        assertThat(awaitKey(cacheKey, false)).isTrue();
    }

    private boolean keyPresent(String key) {
        return Boolean.TRUE.equals(redisTemplate.hasKey(key));
    }

    /**
     * Writes and deletes issued by the cache are not always visible to the next command
     * this test sends, so poll for the expected state instead of reading once.
     */
    private boolean awaitKey(String key, boolean expected) {
        long deadline = System.nanoTime() + Duration.ofSeconds(2).toNanos();
        while (System.nanoTime() < deadline) {
            if (keyPresent(key) == expected) {
                return true;
            }
        }
        return false;
    }
}
