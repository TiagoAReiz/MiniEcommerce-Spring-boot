package reiz.miniecommerce.modules.reviews.adapters.in.controllers;

import reiz.miniecommerce.modules.reviews.core.exceptions.AlreadyReviewedException;
import reiz.miniecommerce.modules.reviews.core.exceptions.NotYourReviewException;
import reiz.miniecommerce.modules.reviews.core.exceptions.OrderNotDeliveredException;
import reiz.miniecommerce.modules.reviews.core.exceptions.ReviewNotFoundException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.core.annotation.Order;
import org.springframework.web.bind.annotation.RestControllerAdvice;

// Ahead of the global net. Spring picks the first advice, in order, that has any method
// matching the exception — not the most specific one across advices — so without an
// explicit order the catch-all could answer for these instead.
@Order(0)
@RestControllerAdvice(assignableTypes = ReviewController.class)
public class ReviewExceptionHandler {

    @ExceptionHandler(ReviewNotFoundException.class)
    public ProblemDetail onMissing(ReviewNotFoundException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, "Não encontrado");
    }

    @ExceptionHandler(NotYourReviewException.class)
    public ProblemDetail onNotYours(NotYourReviewException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN, "A avaliação é de outro cliente");
    }

    @ExceptionHandler(OrderNotDeliveredException.class)
    public ProblemDetail onNotDelivered(OrderNotDeliveredException e) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.CONFLICT, "Só é possível avaliar um pedido entregue");
        problem.setProperty("code", "ORDER_NOT_DELIVERED");
        return problem;
    }

    @ExceptionHandler(AlreadyReviewedException.class)
    public ProblemDetail onDuplicate(AlreadyReviewedException e) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.CONFLICT, "Este item já foi avaliado");
        problem.setProperty("code", "ALREADY_REVIEWED");
        return problem;
    }
}
