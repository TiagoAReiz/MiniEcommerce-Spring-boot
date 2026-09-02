package reiz.miniecommerce.modules.cart.adapters.out.repositories;

import reiz.miniecommerce.modules.cart.core.entities.Cart;
import reiz.miniecommerce.modules.cart.core.interfaces.repositories.CartRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

/**
 * Driven adapter: fulfils the {@link CartRepository} port with Redis.
 *
 * <p>Every write refreshes the TTL, so a cart that is being used stays alive and an
 * abandoned one expires on its own. No cleanup job needed.
 */
@Component
@RequiredArgsConstructor
public class CartRedisAdapter implements CartRepository {

    private static final String KEY_PREFIX = "cart:";
    private static final Duration TTL = Duration.ofDays(7);

    private final RedisTemplate<String, Cart> redisTemplate;

    @Override
    public Cart save(Cart cart) {
        redisTemplate.opsForValue().set(key(cart.getUserId()), cart, TTL);
        return cart;
    }

    @Override
    public Optional<Cart> findByUserId(UUID userId) {
        return Optional.ofNullable(redisTemplate.opsForValue().get(key(userId)));
    }

    @Override
    public void deleteByUserId(UUID userId) {
        redisTemplate.delete(key(userId));
    }

    private String key(UUID userId) {
        return KEY_PREFIX + userId;
    }
}
