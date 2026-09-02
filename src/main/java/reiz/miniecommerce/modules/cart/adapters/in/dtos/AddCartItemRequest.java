package reiz.miniecommerce.modules.cart.adapters.in.dtos;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

/**
 * No price field on purpose: the unit price is read from the product on the server.
 */
public record AddCartItemRequest(
        @NotNull UUID productId,
        @NotNull @Min(value = 1, message = "deve ser pelo menos 1") Integer quantity) {
}
