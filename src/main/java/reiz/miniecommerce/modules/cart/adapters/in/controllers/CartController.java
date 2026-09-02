package reiz.miniecommerce.modules.cart.adapters.in.controllers;

import reiz.miniecommerce.modules.auth.application.services.CurrentUserProvider;
import reiz.miniecommerce.modules.cart.adapters.in.dtos.AddCartItemRequest;
import reiz.miniecommerce.modules.cart.adapters.in.dtos.CartLineResponse;
import reiz.miniecommerce.modules.cart.adapters.in.dtos.CartResponse;
import reiz.miniecommerce.modules.cart.adapters.in.dtos.UpdateCartItemRequest;
import reiz.miniecommerce.modules.cart.application.services.CartService;
import reiz.miniecommerce.modules.cart.core.entities.Cart;
import reiz.miniecommerce.modules.cart.core.entities.CartLine;
import reiz.miniecommerce.modules.products.adapters.in.dtos.ProductPhotoResponse;
import reiz.miniecommerce.modules.products.adapters.in.dtos.ProductResponse;
import reiz.miniecommerce.modules.products.application.services.ProductPhotoService;
import reiz.miniecommerce.modules.products.core.entities.Product;
import reiz.miniecommerce.modules.products.core.entities.ProductPhoto;
import reiz.miniecommerce.modules.products.core.interfaces.repositories.ProductRepository;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/cart")
@RequiredArgsConstructor
public class CartController {

    private final CartService cartService;
    private final CurrentUserProvider currentUser;
    private final ProductRepository productRepository;
    private final ProductPhotoService photoService;

    /** An empty cart is still a cart: 200 with no lines, never 404. */
    @GetMapping
    public CartResponse view() {
        return render(cartService.getOrCreate(currentUser.requireId()));
    }

    @PostMapping("/items")
    public CartResponse addItem(@Valid @RequestBody AddCartItemRequest request) {
        return render(cartService.addProduct(
                currentUser.requireId(), request.productId(), request.quantity()));
    }

    @PatchMapping("/items/{productId}")
    public CartResponse setQuantity(@PathVariable UUID productId,
                                    @Valid @RequestBody UpdateCartItemRequest request) {
        return render(cartService.setQuantity(currentUser.requireId(), productId, request.quantity()));
    }

    @DeleteMapping("/items/{productId}")
    public CartResponse removeItem(@PathVariable UUID productId) {
        return render(cartService.removeProduct(currentUser.requireId(), productId));
    }

    @DeleteMapping
    public ResponseEntity<Void> clear() {
        cartService.clear(currentUser.requireId());
        return ResponseEntity.noContent().build();
    }

    /**
     * Fills each line with its product. Redis stores only ids, quantities and the frozen
     * price, so the display data is fetched here: one query for the products, one for their
     * photos, regardless of how many lines the cart has.
     */
    private CartResponse render(Cart cart) {
        List<UUID> productIds = cart.getLines().stream().map(CartLine::getProductId).toList();

        Map<UUID, Product> products = productIds.stream()
                .map(productRepository::findById)
                .flatMap(java.util.Optional::stream)
                .collect(Collectors.toMap(Product::getId, Function.identity()));

        Map<UUID, List<ProductPhoto>> photos = photoService.listFor(productIds);

        List<CartLineResponse> lines = cart.getLines().stream()
                .map(line -> CartLineResponse.of(line, productOf(products, photos, line.getProductId())))
                .toList();

        BigDecimal total = cart.total();
        return CartResponse.of(lines, total);
    }

    private ProductResponse productOf(Map<UUID, Product> products,
                                      Map<UUID, List<ProductPhoto>> photos,
                                      UUID productId) {
        Product product = products.get(productId);
        if (product == null) {
            return null;
        }
        return ProductResponse.from(product, photos.getOrDefault(productId, List.of()).stream()
                .map(ProductPhotoResponse::from)
                .toList());
    }
}
