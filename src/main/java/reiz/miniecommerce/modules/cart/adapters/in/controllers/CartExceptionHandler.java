package reiz.miniecommerce.modules.cart.adapters.in.controllers;

import reiz.miniecommerce.modules.cart.core.exceptions.CartItemNotFoundException;
import reiz.miniecommerce.modules.products.core.exceptions.InsufficientStockException;
import reiz.miniecommerce.modules.products.core.exceptions.ProductNotFoundException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.core.annotation.Order;
import org.springframework.web.bind.annotation.RestControllerAdvice;

// Ahead of the global net. Spring picks the first advice, in order, that has any method
// matching the exception — not the most specific one across advices — so without an
// explicit order the catch-all could answer for these instead.
@Order(0)
@RestControllerAdvice(assignableTypes = CartController.class)
public class CartExceptionHandler {

    @ExceptionHandler(ProductNotFoundException.class)
    public ProblemDetail onProductMissing(ProductNotFoundException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, "Produto não encontrado");
    }

    @ExceptionHandler(CartItemNotFoundException.class)
    public ProblemDetail onLineMissing(CartItemNotFoundException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, "Item não está no carrinho");
    }

    @ExceptionHandler(InsufficientStockException.class)
    public ProblemDetail onOutOfStock(InsufficientStockException e) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.CONFLICT, "Estoque insuficiente para a quantidade pedida");
        problem.setProperty("code", "INSUFFICIENT_STOCK");
        return problem;
    }
}
