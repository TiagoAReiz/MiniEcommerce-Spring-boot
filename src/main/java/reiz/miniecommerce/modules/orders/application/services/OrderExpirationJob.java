package reiz.miniecommerce.modules.orders.application.services;

import reiz.miniecommerce.modules.orders.adapters.out.config.OrderProperties;
import reiz.miniecommerce.modules.orders.core.entities.Order;
import reiz.miniecommerce.modules.orders.core.interfaces.repositories.OrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * Releases the stock held by orders that were never paid for.
 *
 * <p>Checkout takes the stock before any money exists, so an order the customer walked away
 * from keeps a shelf empty for everyone else. Nothing else in the system notices: payment
 * reconciliation only looks at charges that were opened, so an order where the customer never
 * even reached the payment screen is invisible to it.
 *
 * <p>Cancelling here keeps {@code expiresAt} — that is what separates an abandoned order from
 * one somebody cancelled on purpose, and turns the column into a record of the sales that
 * were lost and of what was in them.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class OrderExpirationJob {

    private final OrderRepository orderRepository;
    private final OrderService orderService;
    private final OrderProperties properties;

    @Scheduled(
            fixedDelayString = "${app.orders.expiration-interval:5m}",
            initialDelayString = "${app.orders.expiration-interval:5m}")
    public void run() {
        sweep(OffsetDateTime.now());
    }

    /**
     * Takes the instant explicitly so the sweep can be exercised without waiting for a
     * reservation to age out.
     *
     * @return how many orders this sweep cancelled
     */
    public int sweep(OffsetDateTime now) {
        List<Order> expired = orderRepository.findExpired(now);
        if (expired.isEmpty()) {
            return 0;
        }

        int cancelled = 0;
        for (Order order : expired.stream().limit(properties.getExpirationBatchSize()).toList()) {
            try {
                if (orderService.expire(order.getId())) {
                    cancelled++;
                }
            } catch (RuntimeException e) {
                // one order that cannot be released must not stop the rest of the sweep
                log.error("Expiration failed for order {}", order.getId(), e);
            }
        }

        if (cancelled > 0) {
            log.info("Expired {} of {} abandoned orders", cancelled, expired.size());
        }
        return cancelled;
    }
}
