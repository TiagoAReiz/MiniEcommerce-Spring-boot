package reiz.miniecommerce.modules.orders.adapters.in.dtos;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

/** Everything else comes from the cart and the token. */
public record CheckoutRequest(@NotNull UUID addressId) {
}
