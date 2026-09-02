package reiz.miniecommerce.modules.address.adapters.in.controllers;

import reiz.miniecommerce.modules.address.core.exceptions.AddressNotFoundException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.core.annotation.Order;
import org.springframework.web.bind.annotation.RestControllerAdvice;

// Ahead of the global net. Spring picks the first advice, in order, that has any method
// matching the exception — not the most specific one across advices — so without an
// explicit order the catch-all could answer for these instead.
@Order(0)
@RestControllerAdvice(assignableTypes = AddressController.class)
public class AddressExceptionHandler {

    @ExceptionHandler(AddressNotFoundException.class)
    public ProblemDetail onNotFound(AddressNotFoundException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, "Endereço não encontrado");
    }

    /**
     * The only integrity violation reachable here is the RESTRICT foreign key from
     * {@code shipments}: an address that already shipped something cannot be removed.
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ProblemDetail onInUse(DataIntegrityViolationException e) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.CONFLICT, "Endereço já usado em um envio e não pode ser removido");
        problem.setProperty("code", "ADDRESS_IN_USE");
        return problem;
    }
}
