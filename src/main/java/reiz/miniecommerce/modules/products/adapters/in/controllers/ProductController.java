package reiz.miniecommerce.modules.products.adapters.in.controllers;

import reiz.miniecommerce.modules.products.adapters.in.dtos.PageResponse;
import reiz.miniecommerce.modules.products.adapters.in.dtos.ProductPhotoResponse;
import reiz.miniecommerce.modules.products.adapters.in.dtos.ProductRequest;
import reiz.miniecommerce.modules.products.adapters.in.dtos.ProductResponse;
import reiz.miniecommerce.modules.products.application.services.ProductPhotoService;
import reiz.miniecommerce.modules.products.application.services.ProductService;
import reiz.miniecommerce.modules.products.core.entities.Product;
import reiz.miniecommerce.modules.products.core.entities.ProductPhoto;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/products")
@RequiredArgsConstructor
public class ProductController {

    private static final int MAX_PAGE_SIZE = 100;

    private final ProductService productService;
    private final ProductPhotoService photoService;

    @GetMapping
    public PageResponse<ProductResponse> search(
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        Page<Product> found = productService.search(q,
                PageRequest.of(Math.max(page, 0), clampSize(size), Sort.by("name")));

        // One query for every photo on this page, then grouped in memory. Resolving photos
        // product by product would be one query per row.
        Map<UUID, List<ProductPhoto>> photosByProduct =
                photoService.listFor(found.getContent().stream().map(Product::getId).toList());

        return PageResponse.of(found, product ->
                ProductResponse.from(product, photosOf(photosByProduct, product.getId())));
    }

    @GetMapping("/{id}")
    public ProductResponse detail(@PathVariable UUID id) {
        Product product = productService.byId(id);
        List<ProductPhotoResponse> photos = photoService.listFor(id).stream()
                .map(ProductPhotoResponse::from)
                .toList();
        return ProductResponse.from(product, photos);
    }

    @PostMapping
    @PreAuthorize("hasRole('OWNER')")
    public ResponseEntity<ProductResponse> create(@Valid @RequestBody ProductRequest request,
                                                  UriComponentsBuilder uri) {
        Product created = productService.create(request.toDomain());
        return ResponseEntity
                .created(uri.path("/products/{id}").build(created.getId()))
                .body(ProductResponse.from(created, List.of()));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('OWNER')")
    public ProductResponse update(@PathVariable UUID id, @Valid @RequestBody ProductRequest request) {
        Product updated = productService.update(id, request.toDomain(), request.active());

        // the photos are unchanged by this call, but answering with an empty list would tell a
        // client re-rendering from the response that the product has none
        return ProductResponse.from(updated, photoService.listFor(id).stream()
                .map(ProductPhotoResponse::from)
                .toList());
    }

    /** Retires the product; it is never removed, because past orders reference it. */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('OWNER')")
    public ResponseEntity<Void> deactivate(@PathVariable UUID id) {
        productService.deactivate(id);
        return ResponseEntity.noContent().build();
    }

    private List<ProductPhotoResponse> photosOf(Map<UUID, List<ProductPhoto>> byProduct, UUID productId) {
        return byProduct.getOrDefault(productId, List.of()).stream()
                .map(ProductPhotoResponse::from)
                .toList();
    }

    private int clampSize(int size) {
        if (size < 1) {
            return 1;
        }
        return Math.min(size, MAX_PAGE_SIZE);
    }
}
