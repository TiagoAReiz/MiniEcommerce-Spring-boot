package reiz.miniecommerce.modules.auth.adapters.in.controllers;

import reiz.miniecommerce.modules.auth.core.exceptions.InvalidGoogleTokenException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.core.annotation.Order;
import org.springframework.web.bind.annotation.RestControllerAdvice;

// Ahead of the global net. Spring picks the first advice, in order, that has any method
// matching the exception — not the most specific one across advices — so without an
// explicit order the catch-all could answer for these instead.
@Order(0)
@RestControllerAdvice
public class AuthExceptionHandler {

    /**
     * A rejected Google token is a failed authentication, not a server error. The reason is
     * logged by the verifier but never echoed back: it would tell a caller probing tokens
     * exactly which check failed.
     */
    @ExceptionHandler(InvalidGoogleTokenException.class)
    public ProblemDetail onInvalidGoogleToken(InvalidGoogleTokenException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED, "Invalid Google ID token");
    }
}
