package reiz.miniecommerce.modules.users.adapters.in.controllers;

import reiz.miniecommerce.modules.users.core.exceptions.CpfAlreadyUsedException;
import reiz.miniecommerce.modules.users.core.exceptions.UserNotFoundException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.core.annotation.Order;
import org.springframework.web.bind.annotation.RestControllerAdvice;

// Ahead of the global net. Spring picks the first advice, in order, that has any method
// matching the exception — not the most specific one across advices — so without an
// explicit order the catch-all could answer for these instead.
@Order(0)
@RestControllerAdvice(assignableTypes = UserController.class)
public class UserExceptionHandler {

    @ExceptionHandler(UserNotFoundException.class)
    public ProblemDetail onNotFound(UserNotFoundException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, "Usuário não encontrado");
    }

    @ExceptionHandler(CpfAlreadyUsedException.class)
    public ProblemDetail onCpfTaken(CpfAlreadyUsedException e) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.CONFLICT, "CPF já cadastrado em outra conta");
        problem.setProperty("code", "CPF_ALREADY_USED");
        return problem;
    }
}
