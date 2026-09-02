package reiz.miniecommerce.modules.payments.adapters.out.redis;

import reiz.miniecommerce.modules.payments.adapters.out.mercadopago.MercadoPagoProperties;
import reiz.miniecommerce.modules.payments.core.interfaces.repositories.NotificationDeduplicator;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * Claims a notification id in Redis.
 *
 * <p>{@code SETNX} is atomic, so two retries arriving at once cannot both win: exactly one
 * gets true and processes the payment.
 */
@Component
@RequiredArgsConstructor
public class RedisNotificationDeduplicator implements NotificationDeduplicator {

    private static final String KEY_PREFIX = "mp:seen:";

    private final StringRedisTemplate redisTemplate;
    private final MercadoPagoProperties properties;

    @Override
    public boolean claim(String notificationId) {
        return Boolean.TRUE.equals(redisTemplate.opsForValue()
                .setIfAbsent(KEY_PREFIX + notificationId, "1", properties.getNotificationMemory()));
    }

    @Override
    public void release(String notificationId) {
        redisTemplate.delete(KEY_PREFIX + notificationId);
    }
}
