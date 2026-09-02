package reiz.miniecommerce.modules.reviews.adapters.in.controllers;

import reiz.miniecommerce.modules.auth.application.services.CurrentUserProvider;
import reiz.miniecommerce.modules.products.adapters.in.dtos.PageResponse;
import reiz.miniecommerce.modules.reviews.adapters.in.dtos.PendingReviewResponse;
import reiz.miniecommerce.modules.reviews.adapters.in.dtos.ProductReviewsResponse;
import reiz.miniecommerce.modules.reviews.adapters.in.dtos.ReviewRequest;
import reiz.miniecommerce.modules.reviews.adapters.in.dtos.ReviewResponse;
import reiz.miniecommerce.modules.reviews.application.services.ReviewService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
public class ReviewController {

    private static final int MAX_PAGE_SIZE = 100;

    private final ReviewService reviewService;
    private final CurrentUserProvider currentUser;

    @GetMapping("/products/{productId}/reviews")
    public ProductReviewsResponse forProduct(@PathVariable UUID productId,
                                             @RequestParam(defaultValue = "0") int page,
                                             @RequestParam(defaultValue = "20") int size) {
        PageResponse<ReviewResponse> reviews = PageResponse.of(
                reviewService.forProduct(productId,
                        PageRequest.of(Math.max(page, 0), clampSize(size),
                                Sort.by(Sort.Direction.DESC, "createdAt"))),
                ReviewResponse::from);

        return new ProductReviewsResponse(
                reviewService.averageRating(productId),
                reviewService.reviewCount(productId),
                reviews);
    }

    @GetMapping("/users/me/pending-reviews")
    public List<PendingReviewResponse> pending() {
        return reviewService.awaitingReview(currentUser.requireId()).stream()
                .map(PendingReviewResponse::from)
                .toList();
    }

    @PostMapping("/order-items/{orderItemId}/reviews")
    @ResponseStatus(HttpStatus.CREATED)
    public ReviewResponse create(@PathVariable UUID orderItemId,
                                 @Valid @RequestBody ReviewRequest request) {
        return ReviewResponse.from(reviewService.create(currentUser.requireId(), orderItemId,
                request.rating(), request.title(), request.comment()));
    }

    @PutMapping("/reviews/{id}")
    public ReviewResponse update(@PathVariable UUID id, @Valid @RequestBody ReviewRequest request) {
        return ReviewResponse.from(reviewService.update(currentUser.requireId(), id,
                request.rating(), request.title(), request.comment()));
    }

    @DeleteMapping("/reviews/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        reviewService.delete(currentUser.requireId(), id);
        return ResponseEntity.noContent().build();
    }

    private int clampSize(int size) {
        if (size < 1) {
            return 1;
        }
        return Math.min(size, MAX_PAGE_SIZE);
    }
}
