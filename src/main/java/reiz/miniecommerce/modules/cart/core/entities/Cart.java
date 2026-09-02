package reiz.miniecommerce.modules.cart.core.entities;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Domain model for a shopping cart. Lives only in Redis: it is short lived,
 * rewritten on every change and discarded once the order is placed.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(of = "userId")
public class Cart {

    private UUID userId;

    @Builder.Default
    private List<CartLine> lines = new ArrayList<>();

    public static Cart empty(UUID userId) {
        return Cart.builder().userId(userId).build();
    }

    /** Adds a product, summing the quantity when the product is already in the cart. */
    public void addLine(UUID productId, int quantity, BigDecimal unitPrice) {
        lines.stream()
                .filter(line -> line.getProductId().equals(productId))
                .findFirst()
                .ifPresentOrElse(
                        line -> line.setQuantity(line.getQuantity() + quantity),
                        () -> lines.add(CartLine.builder()
                                .productId(productId)
                                .quantity(quantity)
                                .unitPrice(unitPrice)
                                .build()));
    }

    public void removeLine(UUID productId) {
        lines.removeIf(line -> line.getProductId().equals(productId));
    }

    public BigDecimal total() {
        return lines.stream()
                .map(CartLine::subtotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public boolean hasNoLines() {
        return lines.isEmpty();
    }
}
