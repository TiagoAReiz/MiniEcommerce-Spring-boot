package reiz.miniecommerce.modules.reviews.core.entities;

import java.time.OffsetDateTime;
import java.util.UUID;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Domain model for Review. Free of persistence concerns: no JPA annotations,
 * no framework types. Other aggregates are referenced by id.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(of = "id")
public class Review {

    private UUID id;
    private UUID orderItemId;
    private UUID productId;
    private String title;
    private String comment;
    private Short rating;

    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
}
