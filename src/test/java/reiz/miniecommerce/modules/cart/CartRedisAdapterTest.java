package reiz.miniecommerce.modules.cart;

import reiz.miniecommerce.modules.cart.core.entities.Cart;
import reiz.miniecommerce.modules.cart.core.interfaces.repositories.CartRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Exercises the Redis adapter itself: round-trip, JSON shape and TTL. Cart rules and the
 * HTTP surface are covered by {@code CartApiTest}.
 */
@SpringBootTest
class CartRedisAdapterTest {

    private static final UUID USER = UUID.randomUUID();
    private static final UUID PRODUCT = UUID.randomUUID();

    @Autowired
    private CartRepository cartRepository;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @AfterEach
    void cleanUp() {
        cartRepository.deleteByUserId(USER);
    }

    @Test
    void storesAndReadsBackACartThroughRedis() {
        Cart cart = Cart.empty(USER);
        cart.addLine(PRODUCT, 2, new BigDecimal("49.90"));
        cartRepository.save(cart);

        Cart stored = cartRepository.findByUserId(USER).orElseThrow();

        assertThat(stored.getUserId()).isEqualTo(USER);
        assertThat(stored.getLines()).hasSize(1);
        assertThat(stored.getLines().get(0).getProductId()).isEqualTo(PRODUCT);
        assertThat(stored.getLines().get(0).getQuantity()).isEqualTo(2);
        assertThat(stored.total()).isEqualByComparingTo("99.80");
    }

    @Test
    void sumsQuantityWhenTheSameProductIsAddedTwice() {
        Cart cart = Cart.empty(USER);
        cart.addLine(PRODUCT, 1, new BigDecimal("10.00"));
        cart.addLine(PRODUCT, 3, new BigDecimal("10.00"));
        cartRepository.save(cart);

        Cart stored = cartRepository.findByUserId(USER).orElseThrow();

        assertThat(stored.getLines()).hasSize(1);
        assertThat(stored.getLines().get(0).getQuantity()).isEqualTo(4);
        assertThat(stored.total()).isEqualByComparingTo("40.00");
    }

    @Test
    void writesJsonUnderAPrefixedKeyWithAnExpiry() {
        Cart cart = Cart.empty(USER);
        cart.addLine(PRODUCT, 1, new BigDecimal("10.00"));
        cartRepository.save(cart);

        String key = "cart:" + USER;
        assertThat(redisTemplate.opsForValue().get(key)).contains(PRODUCT.toString());

        // seven days, minus whatever the round trip took
        assertThat(redisTemplate.getExpire(key)).isGreaterThan(604_000L);
    }

    @Test
    void clearsTheCart() {
        Cart cart = Cart.empty(USER);
        cart.addLine(PRODUCT, 1, new BigDecimal("10.00"));
        cartRepository.save(cart);

        cartRepository.deleteByUserId(USER);

        assertThat(cartRepository.findByUserId(USER)).isEmpty();
    }
}
