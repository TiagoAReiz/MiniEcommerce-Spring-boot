package reiz.miniecommerce.modules.products.adapters.in.dtos;

import reiz.miniecommerce.modules.products.core.entities.Product;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record ProductRequest(
        @NotBlank @Size(max = 255) String name,

        String description,

        @NotNull
        @DecimalMin(value = "0.00", message = "não pode ser negativo")
        @Digits(integer = 10, fraction = 2, message = "use no máximo 2 casas decimais")
        BigDecimal price,

        @NotNull @Min(value = 0, message = "não pode ser negativo") Integer stock,

        /**
         * Only meaningful on update, where omitting it leaves the current value alone.
         * Creation always produces an active product.
         */
        Boolean active) {

    public Product toDomain() {
        return Product.builder()
                .name(name)
                .description(description)
                .price(price)
                .stock(stock)
                .active(true)
                .build();
    }
}
