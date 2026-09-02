package reiz.miniecommerce.modules.cart.application.services;

import reiz.miniecommerce.modules.cart.core.entities.Cart;
import reiz.miniecommerce.modules.cart.core.entities.CartLine;
import reiz.miniecommerce.modules.cart.core.exceptions.CartItemNotFoundException;
import reiz.miniecommerce.modules.products.core.exceptions.InsufficientStockException;
import reiz.miniecommerce.modules.cart.core.interfaces.repositories.CartRepository;
import reiz.miniecommerce.modules.products.core.entities.Product;
import reiz.miniecommerce.modules.products.core.exceptions.ProductNotFoundException;
import reiz.miniecommerce.modules.products.core.interfaces.repositories.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * Cart use cases. Depends on the {@link CartRepository} port, so it has no idea the cart
 * lives in Redis.
 */
@Service
@RequiredArgsConstructor
public class CartService {

    private final CartRepository cartRepository;
    private final ProductRepository productRepository;

    public Cart getOrCreate(UUID userId) {
        return cartRepository.findByUserId(userId).orElseGet(() -> Cart.empty(userId));
    }

    /**
     * Adds units of a product, summing with what is already there.
     *
     * <p>The unit price is read from the product here and never accepted from the caller:
     * a client-supplied price would let anyone buy at whatever value they like. It is frozen
     * into the cart line so the customer sees the price they shopped at, and checkout
     * revalidates it against the product before creating the order.
     */
    public Cart addProduct(UUID userId, UUID productId, int quantity) {
        Cart cart = getOrCreate(userId);
        Product product = sellableProduct(productId);

        int alreadyInCart = quantityOf(cart, productId);
        requireStock(product, alreadyInCart + quantity);

        cart.addLine(productId, quantity, product.getPrice());
        return cartRepository.save(cart);
    }

    /** Sets an absolute quantity. Zero removes the line. */
    public Cart setQuantity(UUID userId, UUID productId, int quantity) {
        Cart cart = getOrCreate(userId);

        if (quantityOf(cart, productId) == 0) {
            throw new CartItemNotFoundException(productId);
        }
        if (quantity == 0) {
            cart.removeLine(productId);
            return cartRepository.save(cart);
        }

        requireStock(sellableProduct(productId), quantity);
        cart.removeLine(productId);
        cart.addLine(productId, quantity, sellableProduct(productId).getPrice());
        return cartRepository.save(cart);
    }

    /** Idempotent: removing something that is not there is not an error. */
    public Cart removeProduct(UUID userId, UUID productId) {
        Cart cart = getOrCreate(userId);
        cart.removeLine(productId);
        return cartRepository.save(cart);
    }

    public void clear(UUID userId) {
        cartRepository.deleteByUserId(userId);
    }

    private Product sellableProduct(UUID productId) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new ProductNotFoundException(productId));

        if (!product.isActive()) {
            throw new InsufficientStockException(productId, 1);
        }
        return product;
    }

    private void requireStock(Product product, int totalRequested) {
        if (!product.isInStock(totalRequested)) {
            throw new InsufficientStockException(product.getId(), totalRequested);
        }
    }

    private int quantityOf(Cart cart, UUID productId) {
        return cart.getLines().stream()
                .filter(line -> line.getProductId().equals(productId))
                .mapToInt(CartLine::getQuantity)
                .findFirst()
                .orElse(0);
    }
}
