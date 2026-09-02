package reiz.miniecommerce.modules.reviews.adapters.in.dtos;

import reiz.miniecommerce.modules.reviews.core.entities.Review;

import java.time.OffsetDateTime;
import java.util.UUID;

public record ReviewResponse(
        UUID id,
        UUID productId,
        UUID orderItemId,
        Short rating,
        String title,
        String comment,
        OffsetDateTime createdAt) {

    public static ReviewResponse from(Review review) {
        return new ReviewResponse(review.getId(), review.getProductId(), review.getOrderItemId(),
                review.getRating(), review.getTitle(), review.getComment(), review.getCreatedAt());
    }
}
