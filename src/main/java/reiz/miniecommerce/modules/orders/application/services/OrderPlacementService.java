package reiz.miniecommerce.modules.orders.application.services;

import reiz.miniecommerce.modules.address.core.interfaces.repositories.AddressRepository;
import reiz.miniecommerce.modules.cart.core.entities.Cart;
import reiz.miniecommerce.modules.cart.core.entities.CartLine;
import reiz.miniecommerce.modules.cart.core.interfaces.repositories.CartRepository;
import reiz.miniecommerce.modules.orders.adapters.out.config.OrderProperties;
import reiz.miniecommerce.modules.orders.core.entities.Order;
import reiz.miniecommerce.modules.orders.core.entities.OrderItem;
import reiz.miniecommerce.modules.orders.core.entities.OrderStatus;
import reiz.miniecommerce.modules.orders.core.exceptions.AddressNotOwnedException;
import reiz.miniecommerce.modules.orders.core.exceptions.CheckoutBlockedException;
import reiz.miniecommerce.modules.orders.core.exceptions.EmptyCartException;
import reiz.miniecommerce.modules.orders.core.exceptions.PriceChangedException;
import reiz.miniecommerce.modules.orders.core.interfaces.repositories.OrderItemRepository;
import reiz.miniecommerce.modules.orders.core.interfaces.repositories.OrderRepository;
import reiz.miniecommerce.modules.products.core.entities.Product;
import reiz.miniecommerce.modules.products.core.exceptions.InsufficientStockException;
import reiz.miniecommerce.modules.products.core.exceptions.ProductNotFoundException;
import reiz.miniecommerce.modules.products.core.interfaces.repositories.ProductRepository;
import reiz.miniecommerce.modules.shipments.core.entities.ShippingQuote;
import reiz.miniecommerce.modules.users.core.entities.User;
import reiz.miniecommerce.modules.users.core.exceptions.UserNotFoundException;
import reiz.miniecommerce.modules.users.core.interfaces.repositories.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * The database half of checkout: everything that has to be all-or-nothing.
 *
 * <p>Split out of {@link CheckoutService} so the freight lookup can happen before this
 * transaction opens rather than inside it. The pool is five connections wide; a third-party
 * HTTP call held between BEGIN and COMMIT would let a slow provider exhaust it and stall
 * requests that have nothing to do with checkout.
 */
@Service
@RequiredArgsConstructor
public class OrderPlacementService {

    private final CartRepository cartRepository;
    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final ProductRepository productRepository;
    private final UserRepository userRepository;
    private final AddressRepository addressRepository;
    private final OrderProperties properties;

    /**
     * The cheap rejections, run before anything external is paid for.
     *
     * <p>Deliberately repeated inside {@link #place}: this one keeps a doomed checkout from
     * spending a CEP lookup, that one is the actual guard. Between the two the customer
     * could delete the address, and only the check inside the write transaction can be
     * trusted to catch it.
     */
    @Transactional(readOnly = true)
    public void requireCheckoutable(UUID userId, UUID addressId) {
        requireCompleteProfile(userId);
        requireOwnAddress(userId, addressId);
        requireSomethingToBuy(userId);
    }

    @Transactional
    public Order place(UUID userId, UUID addressId, ShippingQuote quote) {
        requireCompleteProfile(userId);
        requireOwnAddress(userId, addressId);

        Cart cart = requireSomethingToBuy(userId);

        // The reservation starts ticking here, not when a payment is opened: the stock is
        // already taken, and a customer who never reaches the payment screen would otherwise
        // hold it forever.
        Order order = orderRepository.save(Order.builder()
                .userId(userId)
                .addressId(addressId)
                .status(OrderStatus.PENDING)
                // Frozen with the line prices, and for the same reason: it was quoted from a
                // lookup that can answer differently tomorrow.
                .shippingCost(quote.cost())
                .shippingDistanceKm(quote.distanceKm())
                .expiresAt(OffsetDateTime.now().plus(properties.getReservationWindow()))
                .build());

        for (CartLine line : cart.getLines()) {
            Product product = productRepository.findById(line.getProductId())
                    .orElseThrow(() -> new ProductNotFoundException(line.getProductId()));

            requireUnchangedPrice(product, line);

            // the stock check and the deduction happen in one statement, so two simultaneous
            // checkouts cannot both be told the last unit is theirs
            if (!productRepository.takeStock(product.getId(), line.getQuantity())) {
                throw new InsufficientStockException(product.getId(), line.getQuantity());
            }

            orderItemRepository.save(OrderItem.builder()
                    .orderId(order.getId())
                    .productId(product.getId())
                    .quantity(line.getQuantity())
                    .unitPrice(line.getUnitPrice())
                    .build());
        }

        clearCartOnceCommitted(userId);
        return order;
    }

    /**
     * The cart is not part of the database transaction — Redis cannot roll back with it. So
     * the delete is deferred until the commit actually succeeds; a rollback leaves the
     * customer's cart untouched instead of losing it along with the failed order.
     */
    private void clearCartOnceCommitted(UUID userId) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            cartRepository.deleteByUserId(userId);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                cartRepository.deleteByUserId(userId);
            }
        });
    }

    /**
     * Price is checked again here, not only when the item entered the cart: a cart survives
     * seven days in Redis and the price can move in the meantime. Stock is not checked here —
     * {@code takeStock} checks and deducts atomically, and a check beforehand would only be a
     * second opinion that a concurrent checkout can invalidate.
     */
    private void requireUnchangedPrice(Product product, CartLine line) {
        if (product.getPrice().compareTo(line.getUnitPrice()) != 0) {
            throw new PriceChangedException(product.getId(), line.getUnitPrice(), product.getPrice());
        }
    }

    /**
     * Read twice on purpose, and for the same reason the profile and address checks are: the
     * early call keeps a checkout that was never going to happen from paying for a CEP
     * lookup, and this one is the guard that counts, because the cart lives in Redis and can
     * empty between the two.
     *
     * <p>It also decides which error the customer sees. Quoting first would answer an empty
     * cart with whatever the freight lookup had to say about the shop, which is true but not
     * the reason their checkout failed.
     */
    private Cart requireSomethingToBuy(UUID userId) {
        Cart cart = cartRepository.findByUserId(userId).orElseThrow(EmptyCartException::new);
        if (cart.hasNoLines()) {
            throw new EmptyCartException();
        }
        return cart;
    }

    private void requireCompleteProfile(UUID userId) {
        User user = userRepository.findById(userId).orElseThrow(() -> new UserNotFoundException(userId));
        if (!user.canCheckout()) {
            throw new CheckoutBlockedException();
        }
    }

    private void requireOwnAddress(UUID userId, UUID addressId) {
        boolean owned = addressRepository.findById(addressId)
                .filter(address -> userId.equals(address.getUserId()))
                .isPresent();

        if (!owned) {
            throw new AddressNotOwnedException(addressId);
        }
    }
}
