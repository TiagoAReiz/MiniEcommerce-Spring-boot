package reiz.miniecommerce.modules.products.adapters.in.dtos;

import reiz.miniecommerce.modules.products.core.entities.Product;
import reiz.miniecommerce.modules.products.core.entities.ProductHighlight;
import reiz.miniecommerce.modules.products.core.entities.ProductSpec;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * A product with its photos. Which photo is the cover is carried by {@code isCover} on each
 * photo — the server does not single one out, the client picks.
 */
public record ProductResponse(
        UUID id,
        String name,
        String description,
        BigDecimal price,
        Integer stock,
        boolean active,
        boolean sellable,
        String category,
        List<ProductHighlight> highlights,
        List<ProductSpec> specs,
        List<ProductPhotoResponse> photos,
        OffsetDateTime createdAt) {

    public static ProductResponse from(Product product, List<ProductPhotoResponse> photos) {
        return new ProductResponse(
                product.getId(),
                product.getName(),
                product.getDescription(),
                product.getPrice(),
                product.getStock(),
                product.isActive(),
                product.isSellable(),
                product.getCategory(),
                product.getHighlights(),
                product.getSpecs(),
                photos,
                product.getCreatedAt());
    }
}
