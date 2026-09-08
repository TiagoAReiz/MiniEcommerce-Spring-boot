package reiz.miniecommerce.modules.orders.adapters.in.controllers;

import reiz.miniecommerce.modules.auth.application.services.CurrentUserProvider;
import reiz.miniecommerce.modules.orders.adapters.in.dtos.CheckoutRequest;
import reiz.miniecommerce.modules.orders.adapters.in.dtos.OrderItemResponse;
import reiz.miniecommerce.modules.orders.adapters.in.dtos.OrderResponse;
import reiz.miniecommerce.modules.orders.adapters.in.dtos.UpdateOrderStatusRequest;
import reiz.miniecommerce.modules.orders.application.services.CheckoutService;
import reiz.miniecommerce.modules.orders.application.services.OrderService;
import reiz.miniecommerce.modules.orders.core.entities.Order;
import reiz.miniecommerce.modules.orders.core.entities.OrderItem;
import reiz.miniecommerce.modules.products.adapters.in.dtos.PageResponse;
import reiz.miniecommerce.modules.products.adapters.in.dtos.ProductPhotoResponse;
import reiz.miniecommerce.modules.products.adapters.in.dtos.ProductResponse;
import reiz.miniecommerce.modules.products.application.services.ProductPhotoService;
import reiz.miniecommerce.modules.products.core.entities.Product;
import reiz.miniecommerce.modules.products.core.entities.ProductPhoto;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/orders")
@RequiredArgsConstructor
public class OrderController {

    private static final int MAX_PAGE_SIZE = 100;

    private final CheckoutService checkoutService;
    private final OrderService orderService;
    private final ProductPhotoService photoService;
    private final CurrentUserProvider currentUser;

    @PostMapping
    public ResponseEntity<OrderResponse> checkout(@Valid @RequestBody CheckoutRequest request,
                                                  UriComponentsBuilder uri) {
        Order order = checkoutService.checkout(currentUser.requireId(), request.addressId());

        return ResponseEntity
                .created(uri.path("/orders/{id}").build(order.getId()))
                .body(render(order));
    }

    @GetMapping
    public PageResponse<OrderResponse> myOrders(@RequestParam(defaultValue = "0") int page,
                                                @RequestParam(defaultValue = "20") int size) {
        return PageResponse.of(
                orderService.ordersOf(currentUser.requireId(),
                        PageRequest.of(Math.max(page, 0), clampSize(size))),
                this::render);
    }

    /**
     * A loja inteira, para o dono. Mesmo formato de página da busca de produtos.
     *
     * <p>Rota própria em vez de um desvio por papel dentro de {@code GET /orders}: uma rota
     * que devolve coisas diferentes conforme quem chama é a que engana em revisão, e o dono
     * também é cliente — ele precisa continuar conseguindo ver os pedidos dele.
     *
     * <p>O caminho literal {@code /all} não colide com {@code /{id}}: o Spring casa o literal
     * antes do template. Mas é por isso que ele não pode ser um UUID válido algum dia.
     */
    @GetMapping("/all")
    @PreAuthorize("hasRole('OWNER')")
    public PageResponse<OrderResponse> allOrders(@RequestParam(defaultValue = "0") int page,
                                                 @RequestParam(defaultValue = "20") int size) {
        return PageResponse.of(
                orderService.allOrders(PageRequest.of(Math.max(page, 0), clampSize(size))),
                this::render);
    }

    @GetMapping("/{id}")
    public OrderResponse detail(@PathVariable UUID id) {
        return render(orderService.visibleOrder(id, currentUser.requireId(), callerIsOwner()));
    }

    @PatchMapping("/{id}/status")
    @PreAuthorize("hasRole('OWNER')")
    public OrderResponse changeStatus(@PathVariable UUID id,
                                      @Valid @RequestBody UpdateOrderStatusRequest request) {
        return render(orderService.changeStatus(id, request.status()));
    }

    private boolean callerIsOwner() {
        return SecurityContextHolder.getContext().getAuthentication().getAuthorities().stream()
                .anyMatch(authority -> "ROLE_OWNER".equals(authority.getAuthority()));
    }

    private OrderResponse render(Order order) {
        List<OrderItem> items = orderService.itemsOf(order.getId());

        Map<UUID, Product> products = orderService.productsOf(items).stream()
                .collect(Collectors.toMap(Product::getId, Function.identity()));

        Map<UUID, List<ProductPhoto>> photos =
                photoService.listFor(items.stream().map(OrderItem::getProductId).toList());

        List<OrderItemResponse> lines = items.stream()
                .map(item -> OrderItemResponse.of(item, productOf(products, photos, item.getProductId())))
                .toList();

        // Goods only. The delivery fee is frozen on the order, and OrderResponse adds the
        // two into the total — the same total OrderService#totalOf hands the payment gateway.
        BigDecimal itemsTotal = lines.stream()
                .map(OrderItemResponse::subtotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return OrderResponse.of(order, lines, itemsTotal);
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

    private int clampSize(int size) {
        if (size < 1) {
            return 1;
        }
        return Math.min(size, MAX_PAGE_SIZE);
    }
}
