package reiz.miniecommerce.modules.payments.adapters.in.controllers;

import reiz.miniecommerce.modules.orders.core.exceptions.OrderNotFoundException;
import reiz.miniecommerce.modules.payments.core.exceptions.OrderAlreadyPaidException;
import reiz.miniecommerce.modules.payments.core.exceptions.PaymentGatewayException;
import reiz.miniecommerce.modules.payments.core.exceptions.PaymentNotFoundException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.core.annotation.Order;
import org.springframework.web.bind.annotation.RestControllerAdvice;

// Ahead of the global net. Spring picks the first advice, in order, that has any method
// matching the exception — not the most specific one across advices — so without an
// explicit order the catch-all could answer for these instead.
@Order(0)
@RestControllerAdvice(assignableTypes = PaymentController.class)
public class PaymentExceptionHandler {

    @ExceptionHandler({PaymentNotFoundException.class, OrderNotFoundException.class})
    public ProblemDetail onMissing(RuntimeException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, "Não encontrado");
    }

    @ExceptionHandler(OrderAlreadyPaidException.class)
    public ProblemDetail onAlreadyPaid(OrderAlreadyPaidException e) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.CONFLICT, "Pedido não está aguardando pagamento");
        problem.setProperty("code", "ORDER_ALREADY_PAID");
        return problem;
    }

    /**
     * 502, not 500: the failure is downstream. It tells the client the request was fine and
     * retrying later may well work.
     */
    @ExceptionHandler(PaymentGatewayException.class)
    public ProblemDetail onGatewayDown(PaymentGatewayException e) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_GATEWAY, "Não foi possível falar com o Mercado Pago");
        problem.setProperty("code", "PAYMENT_GATEWAY_ERROR");
        return problem;
    }
}
