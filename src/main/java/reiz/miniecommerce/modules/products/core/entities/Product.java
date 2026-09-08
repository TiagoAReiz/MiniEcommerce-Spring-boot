package reiz.miniecommerce.modules.products.core.entities;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Domain model for Product. Free of persistence concerns: no JPA annotations,
 * no framework types. Other aggregates are referenced by id.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(of = "id")
public class Product {

    private UUID id;
    private String name;
    private String description;
    private BigDecimal price;
    private Integer stock;
    private boolean active;

    /**
     * What the catalogue filters by. Null means the product answers only to "Todos" — the
     * filter bar is built from the categories that are actually in use, so an unfilled one
     * costs the product a place in the bar but never hides it from the full listing.
     */
    private String category;

    /** The headline figures on the card, in the order they are shown. Never null; may be empty. */
    @Builder.Default
    private List<ProductHighlight> highlights = List.of();

    /** The spec sheet, in the operator's order. Never null; may be empty. */
    @Builder.Default
    private List<ProductSpec> specs = List.of();

    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;

    public boolean isInStock(int quantity) {
        return stock != null && stock >= quantity;
    }

    /** What the catalogue shows: retired products and sold-out ones stay out of it. */
    public boolean isSellable() {
        return active && isInStock(1);
    }

    /** Restocking brings the product back; a product retired by hand needs a manual return. */
    public void restock(int quantity) {
        stock += quantity;
        if (stock > 0) {
            active = true;
        }
    }
}
