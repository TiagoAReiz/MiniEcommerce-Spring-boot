package reiz.miniecommerce.modules.cart.adapters.in.dtos;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/** Absolute quantity, unlike the POST which adds to what is there. Zero removes the line. */
public record UpdateCartItemRequest(
        @NotNull @Min(value = 0, message = "não pode ser negativo") Integer quantity) {
}
