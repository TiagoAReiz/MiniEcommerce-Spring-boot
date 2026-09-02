package reiz.miniecommerce.modules.cart.adapters.out.repositories.config;

import tools.jackson.databind.ObjectMapper;
import reiz.miniecommerce.modules.cart.core.entities.Cart;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.JacksonJsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

/**
 * Wiring for the cart's Redis access.
 *
 * <p>Values are stored as JSON rather than with the default JDK serializer: JDK
 * serialization would force {@link Cart} to implement {@code Serializable} and would
 * break every stored cart as soon as a field is added.
 */
@Configuration
public class CartRedisConfig {

    @Bean
    public RedisTemplate<String, Cart> cartRedisTemplate(RedisConnectionFactory connectionFactory,
                                                         ObjectMapper objectMapper) {
        RedisTemplate<String, Cart> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);
        template.setKeySerializer(new StringRedisSerializer());
        template.setValueSerializer(new JacksonJsonRedisSerializer<>(objectMapper, Cart.class));
        template.afterPropertiesSet();
        return template;
    }
}
