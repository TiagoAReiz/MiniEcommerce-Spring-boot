package reiz.miniecommerce.modules.orders.adapters.in.dtos;

import reiz.miniecommerce.modules.orders.core.entities.OrderItem;
import reiz.miniecommerce.modules.products.adapters.in.dtos.ProductResponse;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * {@code unitPrice} is the price the order was placed at, frozen in the database. It stays
 * put even if the product is repriced later, which is what keeps order history truthful.
 */
public record OrderItemResponse(
        UUID id,
        ProductResponse product,
        Integer quantity,
        BigDecimal unitPrice,
        BigDecimal subtotal) {

    public static OrderItemResponse of(OrderItem item, ProductResponse product) {
        return new OrderItemResponse(item.getId(), product, item.getQuantity(),
                item.getUnitPrice(), item.subtotal());
    }
}
