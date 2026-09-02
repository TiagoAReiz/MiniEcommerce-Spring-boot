package reiz.miniecommerce.modules.orders.application.services;

import reiz.miniecommerce.modules.address.core.interfaces.repositories.AddressRepository;
import reiz.miniecommerce.modules.cart.core.entities.Cart;
import reiz.miniecommerce.modules.cart.core.entities.CartLine;
import reiz.miniecommerce.modules.cart.core.interfaces.repositories.CartRepository;
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
import reiz.miniecommerce.modules.users.core.entities.User;
import reiz.miniecommerce.modules.users.core.exceptions.UserNotFoundException;
import reiz.miniecommerce.modules.users.core.interfaces.repositories.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.List;
import java.util.UUID;

/**
 * Turns a cart into an order.
 *
 * <p>The single most delicate operation in the system: it reads state that lives in Redis,
 * writes three tables, and moves stock. Everything that touches Postgres runs in one
 * transaction, so a failure anywhere leaves no half-made order and no stock quietly missing.
 */
@Service
@RequiredArgsConstructor
public class CheckoutService {

    private final CartRepository cartRepository;
    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final ProductRepository productRepository;
    private final UserRepository userRepository;
    private final AddressRepository addressRepository;

    @Transactional
    public Order checkout(UUID userId, UUID addressId) {
        requireCompleteProfile(userId);
        requireOwnAddress(userId, addressId);

        Cart cart = cartRepository.findByUserId(userId).orElseThrow(EmptyCartException::new);
        if (cart.hasNoLines()) {
            throw new EmptyCartException();
        }

        Order order = orderRepository.save(Order.builder()
                .userId(userId)
                .addressId(addressId)
                .status(OrderStatus.PENDING)
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

    /** Convenience for the response: the items just written for this order. */
    @Transactional(readOnly = true)
    public List<OrderItem> itemsOf(UUID orderId) {
        return orderItemRepository.findByOrderId(orderId);
    }
}
