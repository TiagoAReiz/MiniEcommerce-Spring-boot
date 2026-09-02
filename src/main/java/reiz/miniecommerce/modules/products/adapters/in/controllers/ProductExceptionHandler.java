package reiz.miniecommerce.modules.products.adapters.in.controllers;

import reiz.miniecommerce.modules.products.core.exceptions.InvalidPhotoException;
import reiz.miniecommerce.modules.products.core.exceptions.ProductNotFoundException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.core.annotation.Order;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

// Ahead of the global net. Spring picks the first advice, in order, that has any method
// matching the exception — not the most specific one across advices — so without an
// explicit order the catch-all could answer for these instead.
@Order(0)
@RestControllerAdvice(assignableTypes = {ProductController.class, ProductPhotoController.class})
public class ProductExceptionHandler {

    @ExceptionHandler(ProductNotFoundException.class)
    public ProblemDetail onNotFound(ProductNotFoundException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, "Produto não encontrado");
    }

    @ExceptionHandler(InvalidPhotoException.class)
    public ProblemDetail onInvalidPhoto(InvalidPhotoException e) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, e.getMessage());
        problem.setProperty("code", "INVALID_PHOTO");
        return problem;
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ProblemDetail onTooLarge(MaxUploadSizeExceededException e) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.PAYLOAD_TOO_LARGE, "Arquivo acima do limite de 5MB");
        problem.setProperty("code", "FILE_TOO_LARGE");
        return problem;
    }
}
