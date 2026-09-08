package reiz.miniecommerce.modules.orders.adapters.in.controllers;

import reiz.miniecommerce.modules.orders.core.exceptions.AddressNotOwnedException;
import reiz.miniecommerce.modules.orders.core.exceptions.CheckoutBlockedException;
import reiz.miniecommerce.modules.orders.core.exceptions.EmptyCartException;
import reiz.miniecommerce.modules.orders.core.exceptions.InvalidStatusTransitionException;
import reiz.miniecommerce.modules.orders.core.exceptions.OrderNotFoundException;
import reiz.miniecommerce.modules.orders.core.exceptions.PriceChangedException;
import reiz.miniecommerce.modules.products.core.exceptions.InsufficientStockException;
import reiz.miniecommerce.modules.shipments.core.exceptions.ShippingOriginNotConfiguredException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.core.annotation.Order;
import org.springframework.web.bind.annotation.RestControllerAdvice;

// Ahead of the global net. Spring picks the first advice, in order, that has any method
// matching the exception — not the most specific one across advices — so without an
// explicit order the catch-all could answer for these instead.
@Order(0)
@RestControllerAdvice(assignableTypes = OrderController.class)
public class OrderExceptionHandler {

    @ExceptionHandler(OrderNotFoundException.class)
    public ProblemDetail onOrderMissing(OrderNotFoundException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, "Pedido não encontrado");
    }

    @ExceptionHandler(AddressNotOwnedException.class)
    public ProblemDetail onAddressMissing(AddressNotOwnedException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, "Endereço não encontrado");
    }

    @ExceptionHandler(CheckoutBlockedException.class)
    public ProblemDetail onIncompleteProfile(CheckoutBlockedException e) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.FORBIDDEN, "Informe CPF e telefone antes de fechar o pedido");
        problem.setProperty("code", "CHECKOUT_BLOCKED");
        return problem;
    }

    @ExceptionHandler(EmptyCartException.class)
    public ProblemDetail onEmptyCart(EmptyCartException e) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.CONFLICT, "Carrinho vazio ou expirado");
        problem.setProperty("code", "EMPTY_CART");
        return problem;
    }

    /**
     * 409 with a stable code rather than a 500, even though the cause is on the shop's side
     * and nothing the customer did. A 500 would tell the front end only that something broke;
     * this lets it say the shop is not taking orders yet, which is what is actually true. The
     * detail stays vague on purpose — a customer has no use for the shop's configuration.
     */
    @ExceptionHandler(ShippingOriginNotConfiguredException.class)
    public ProblemDetail onNoShippingOrigin(ShippingOriginNotConfiguredException e) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.CONFLICT, "A loja ainda não está aceitando pedidos");
        problem.setProperty("code", "SHIPPING_ORIGIN_NOT_CONFIGURED");
        return problem;
    }

    @ExceptionHandler(InsufficientStockException.class)
    public ProblemDetail onOutOfStock(InsufficientStockException e) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.CONFLICT, "Estoque acabou antes do fechamento do pedido");
        problem.setProperty("code", "INSUFFICIENT_STOCK");
        return problem;
    }

    @ExceptionHandler(PriceChangedException.class)
    public ProblemDetail onPriceChanged(PriceChangedException e) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.CONFLICT, "O preço mudou desde que o item entrou no carrinho");
        problem.setProperty("code", "PRICE_CHANGED");
        return problem;
    }

    @ExceptionHandler(InvalidStatusTransitionException.class)
    public ProblemDetail onBadTransition(InvalidStatusTransitionException e) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, e.getMessage());
        problem.setProperty("code", "INVALID_STATUS_TRANSITION");
        return problem;
    }
}
