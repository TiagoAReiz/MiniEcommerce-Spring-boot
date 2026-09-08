package reiz.miniecommerce.modules.products.adapters.in.dtos;

import reiz.miniecommerce.modules.products.core.entities.Product;
import reiz.miniecommerce.modules.products.core.entities.ProductHighlight;
import reiz.miniecommerce.modules.products.core.entities.ProductSpec;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.List;

public record ProductRequest(
        @NotBlank @Size(max = 255) String name,

        String description,

        @NotNull
        @DecimalMin(value = "0.00", message = "não pode ser negativo")
        @Digits(integer = 10, fraction = 2, message = "use no máximo 2 casas decimais")
        BigDecimal price,

        @NotNull @Min(value = 0, message = "não pode ser negativo") Integer stock,

        @Size(max = 60) String category,

        /**
         * Capped at three because that is what the card and the product header are laid out
         * for. A fourth would not be shown, and silently dropping it is worse than refusing.
         */
        @Size(max = 3, message = "no máximo 3 destaques")
        List<@Valid HighlightRequest> highlights,

        @Size(max = 30, message = "no máximo 30 linhas de ficha técnica")
        List<@Valid SpecRequest> specs,

        /**
         * Only meaningful on update, where omitting it leaves the current value alone.
         * Creation always produces an active product.
         */
        Boolean active) {

    /** A headline figure: the number and the unit beside it, typeset differently. */
    public record HighlightRequest(
            @NotBlank @Size(max = 24) String value,
            @Size(max = 24) String unit) {
    }

    /** One row of the spec sheet. */
    public record SpecRequest(
            @NotBlank @Size(max = 60) String label,
            @NotBlank @Size(max = 160) String value) {
    }

    public Product toDomain() {
        return Product.builder()
                .name(name)
                .description(description)
                .price(price)
                .stock(stock)
                .active(true)
                .category(category)
                .highlights(highlightsOrEmpty())
                .specs(specsOrEmpty())
                .build();
    }

    /**
     * An omitted list is an empty one, not "leave what is there". A product edit sends the
     * whole sheet, so treating absence as "unchanged" would make it impossible to clear a
     * spec row that was typed by mistake.
     */
    public List<ProductHighlight> highlightsOrEmpty() {
        return highlights == null ? List.of()
                : highlights.stream().map(h -> new ProductHighlight(h.value(), h.unit())).toList();
    }

    public List<ProductSpec> specsOrEmpty() {
        return specs == null ? List.of()
                : specs.stream().map(s -> new ProductSpec(s.label(), s.value())).toList();
    }
}
