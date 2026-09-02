package reiz.miniecommerce.modules.orders.adapters.in.dtos;

import reiz.miniecommerce.modules.orders.core.entities.OrderStatus;
import jakarta.validation.constraints.NotNull;

public record UpdateOrderStatusRequest(@NotNull OrderStatus status) {
}
