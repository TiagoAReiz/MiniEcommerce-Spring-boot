package reiz.miniecommerce.modules.orders.application.services;

import reiz.miniecommerce.modules.orders.core.entities.Order;
import reiz.miniecommerce.modules.orders.core.entities.OrderItem;
import reiz.miniecommerce.modules.orders.core.interfaces.repositories.OrderItemRepository;
import reiz.miniecommerce.modules.shipments.application.services.ShippingQuoteService;
import reiz.miniecommerce.modules.shipments.core.entities.ShippingQuote;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Turns a cart into an order.
 *
 * <p>Two steps, in this order and for a reason. First the freight is quoted, which reaches
 * out to a third-party CEP service; then {@link OrderPlacementService} writes three tables
 * in one transaction. Doing it the other way round — quoting from inside the transaction —
 * would hold one of the five pooled connections open across the network call.
 *
 * <p>This class is intentionally not annotated: the transaction has to start at the
 * delegate, and a {@code @Transactional} method invoked on {@code this} would bypass the
 * proxy and quietly run without one.
 */
@Service
@RequiredArgsConstructor
public class CheckoutService {

    private final OrderPlacementService placementService;
    private final ShippingQuoteService shippingQuoteService;
    private final OrderItemRepository orderItemRepository;

    public Order checkout(UUID userId, UUID addressId) {
        // Rejected checkouts should not cost a CEP lookup, and an address the caller does not
        // own should never be resolved to coordinates on their behalf.
        placementService.requireCheckoutable(userId, addressId);

        ShippingQuote quote = shippingQuoteService.quoteFor(addressId);

        return placementService.place(userId, addressId, quote);
    }

    /** Convenience for the response: the items just written for this order. */
    @Transactional(readOnly = true)
    public List<OrderItem> itemsOf(UUID orderId) {
        return orderItemRepository.findByOrderId(orderId);
    }
}
