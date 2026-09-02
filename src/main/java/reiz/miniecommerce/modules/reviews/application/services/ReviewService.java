package reiz.miniecommerce.modules.reviews.application.services;

import reiz.miniecommerce.modules.orders.core.entities.Order;
import reiz.miniecommerce.modules.orders.core.entities.OrderItem;
import reiz.miniecommerce.modules.orders.core.entities.OrderStatus;
import reiz.miniecommerce.modules.orders.core.interfaces.repositories.OrderItemRepository;
import reiz.miniecommerce.modules.orders.core.interfaces.repositories.OrderRepository;
import reiz.miniecommerce.modules.reviews.core.entities.Review;
import reiz.miniecommerce.modules.reviews.core.exceptions.AlreadyReviewedException;
import reiz.miniecommerce.modules.reviews.core.exceptions.NotYourReviewException;
import reiz.miniecommerce.modules.reviews.core.exceptions.OrderNotDeliveredException;
import reiz.miniecommerce.modules.reviews.core.exceptions.ReviewNotFoundException;
import reiz.miniecommerce.modules.reviews.core.interfaces.repositories.ReviewRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ReviewService {

    private final ReviewRepository reviewRepository;
    private final OrderItemRepository orderItemRepository;
    private final OrderRepository orderRepository;

    @Transactional(readOnly = true)
    public Page<Review> forProduct(UUID productId, Pageable pageable) {
        return reviewRepository.findByProductId(productId, pageable);
    }

    @Transactional(readOnly = true)
    public double averageRating(UUID productId) {
        return reviewRepository.averageRatingFor(productId);
    }

    @Transactional(readOnly = true)
    public long reviewCount(UUID productId) {
        return reviewRepository.countByProductId(productId);
    }

    /** Delivered items this customer has not rated yet — what the reminder is built from. */
    @Transactional(readOnly = true)
    public List<OrderItem> awaitingReview(UUID userId) {
        return orderItemRepository.findAwaitingReview(OrderStatus.DELIVERED, userId);
    }

    /**
     * Records a review against one purchased item.
     *
     * <p>Three things have to hold: the item was bought by this customer, the order actually
     * arrived, and it has not been rated already. The last one is also a unique constraint in
     * the database, so a double submission cannot slip through between the check and the
     * insert.
     */
    @Transactional
    public Review create(UUID userId, UUID orderItemId, short rating, String title, String comment) {
        OrderItem item = ownItem(userId, orderItemId);

        if (reviewRepository.existsByOrderItemId(orderItemId)) {
            throw new AlreadyReviewedException();
        }

        return reviewRepository.save(Review.builder()
                .orderItemId(orderItemId)
                .productId(item.getProductId())
                .rating(rating)
                .title(title)
                .comment(comment)
                .build());
    }

    @Transactional
    public Review update(UUID userId, UUID reviewId, short rating, String title, String comment) {
        Review review = ownReview(userId, reviewId);
        review.setRating(rating);
        review.setTitle(title);
        review.setComment(comment);
        return reviewRepository.save(review);
    }

    @Transactional
    public void delete(UUID userId, UUID reviewId) {
        reviewRepository.deleteById(ownReview(userId, reviewId).getId());
    }

    private OrderItem ownItem(UUID userId, UUID orderItemId) {
        OrderItem item = orderItemRepository.findById(orderItemId)
                .orElseThrow(() -> new ReviewNotFoundException(orderItemId));

        Order order = orderRepository.findById(item.getOrderId())
                .orElseThrow(() -> new ReviewNotFoundException(orderItemId));

        // an item from someone else's order is reported as missing, not as forbidden
        if (!userId.equals(order.getUserId())) {
            throw new ReviewNotFoundException(orderItemId);
        }
        if (!order.getStatus().allowsReview()) {
            throw new OrderNotDeliveredException();
        }
        return item;
    }

    private Review ownReview(UUID userId, UUID reviewId) {
        Review review = reviewRepository.findById(reviewId)
                .orElseThrow(() -> new ReviewNotFoundException(reviewId));

        OrderItem item = orderItemRepository.findById(review.getOrderItemId())
                .orElseThrow(() -> new ReviewNotFoundException(reviewId));

        boolean mine = orderRepository.findById(item.getOrderId())
                .map(order -> userId.equals(order.getUserId()))
                .orElse(false);

        if (!mine) {
            throw new NotYourReviewException();
        }
        return review;
    }
}
