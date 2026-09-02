package reiz.miniecommerce.modules.shipments.adapters.in.controllers;

import reiz.miniecommerce.modules.orders.core.exceptions.OrderNotFoundException;
import reiz.miniecommerce.modules.shipments.core.exceptions.OrderNotPaidException;
import reiz.miniecommerce.modules.shipments.core.exceptions.ShipmentAlreadyExistsException;
import reiz.miniecommerce.modules.shipments.core.exceptions.ShipmentNotFoundException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.core.annotation.Order;
import org.springframework.web.bind.annotation.RestControllerAdvice;

// Ahead of the global net. Spring picks the first advice, in order, that has any method
// matching the exception — not the most specific one across advices — so without an
// explicit order the catch-all could answer for these instead.
@Order(0)
@RestControllerAdvice(assignableTypes = ShipmentController.class)
public class ShipmentExceptionHandler {

    @ExceptionHandler({ShipmentNotFoundException.class, OrderNotFoundException.class})
    public ProblemDetail onMissing(RuntimeException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, "Não encontrado");
    }

    @ExceptionHandler(OrderNotPaidException.class)
    public ProblemDetail onNotPaid(OrderNotPaidException e) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.CONFLICT, "Pedido ainda não foi pago");
        problem.setProperty("code", "ORDER_NOT_PAID");
        return problem;
    }

    @ExceptionHandler(ShipmentAlreadyExistsException.class)
    public ProblemDetail onDuplicate(ShipmentAlreadyExistsException e) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.CONFLICT, "Pedido já tem envio criado");
        problem.setProperty("code", "SHIPMENT_ALREADY_EXISTS");
        return problem;
    }
}
