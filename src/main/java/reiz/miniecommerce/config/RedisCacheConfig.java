package reiz.miniecommerce.config;

import org.springframework.boot.cache.autoconfigure.RedisCacheManagerBuilderCustomizer;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.serializer.GenericJacksonJsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext.SerializationPair;
import org.springframework.data.redis.serializer.StringRedisSerializer;
import tools.jackson.databind.jsontype.BasicPolymorphicTypeValidator;
import tools.jackson.databind.jsontype.PolymorphicTypeValidator;

import java.time.Duration;

/**
 * Read-through cache backed by Redis, applied with {@code @Cacheable} on the repository
 * adapters. The core and the services never see it.
 *
 * <p>Values are stored as JSON. The default JDK serializer would force every cached
 * domain class to implement {@code Serializable} and would break stored entries as soon
 * as a field is added.
 */
@Configuration
@EnableCaching
public class RedisCacheConfig {

    private static final Duration DEFAULT_TTL = Duration.ofMinutes(10);

    /**
     * Postal-code coordinates get their own, far longer window: a CEP does not move. The
     * store's own origin is resolved on every checkout, so this is also what keeps a
     * provider outage from being felt on most orders.
     */
    private static final Duration CEP_TTL = Duration.ofDays(30);

    /**
     * Default typing writes the concrete class into the payload, so a cached value comes
     * back as its domain type instead of a {@code LinkedHashMap}. The validator keeps that
     * from turning into a deserialization gadget: only our own classes and the JDK types
     * used by the domain are accepted.
     */
    @Bean
    public PolymorphicTypeValidator cacheTypeValidator() {
        return BasicPolymorphicTypeValidator.builder()
                .allowIfSubType("reiz.miniecommerce.")
                .allowIfSubType("java.util.")
                .allowIfSubType("java.math.")
                .allowIfSubType("java.time.")
                .build();
    }

    @Bean
    public RedisCacheManagerBuilderCustomizer cepCoordinatesTtl(RedisCacheConfiguration defaults) {
        return builder -> builder.withCacheConfiguration("cepCoordinates", defaults.entryTtl(CEP_TTL));
    }

    @Bean
    public RedisCacheConfiguration redisCacheConfiguration(PolymorphicTypeValidator validator) {
        GenericJacksonJsonRedisSerializer serializer = GenericJacksonJsonRedisSerializer.builder()
                .enableDefaultTyping(validator)
                .build();

        return RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(DEFAULT_TTL)
                .disableCachingNullValues()
                .serializeKeysWith(SerializationPair.fromSerializer(new StringRedisSerializer()))
                .serializeValuesWith(SerializationPair.fromSerializer(serializer));
    }
}
