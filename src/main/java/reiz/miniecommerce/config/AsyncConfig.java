package reiz.miniecommerce.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import reiz.miniecommerce.modules.orders.adapters.out.config.OrderProperties;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Enables {@code @Async}, used to get slow work off the request thread.
 *
 * <p>The webhook is the case that forced this: it has to answer the payment provider in
 * under a couple of seconds, and confirming a payment means calling their API back.
 *
 * <p>Scheduling is enabled here too, for the reconciliation sweep that catches payments the
 * webhook failed to settle.
 */
@Configuration
@EnableAsync
@EnableScheduling
@EnableConfigurationProperties(OrderProperties.class)
public class AsyncConfig {
}
