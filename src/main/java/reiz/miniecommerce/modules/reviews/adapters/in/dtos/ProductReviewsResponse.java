package reiz.miniecommerce.modules.reviews.adapters.in.dtos;

import reiz.miniecommerce.modules.products.adapters.in.dtos.PageResponse;

/**
 * A page of reviews plus the figures a product page shows next to the stars. The average is
 * over every review, not only the page being returned.
 */
public record ProductReviewsResponse(
        double averageRating,
        long totalReviews,
        PageResponse<ReviewResponse> reviews) {
}
