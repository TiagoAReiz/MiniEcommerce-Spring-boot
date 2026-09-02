package reiz.miniecommerce.modules.cart.adapters.in.dtos;

import java.math.BigDecimal;
import java.util.List;

public record CartResponse(List<CartLineResponse> lines, BigDecimal total, int itemCount) {

    public static CartResponse of(List<CartLineResponse> lines, BigDecimal total) {
        int items = lines.stream().mapToInt(CartLineResponse::quantity).sum();
        return new CartResponse(lines, total, items);
    }
}
