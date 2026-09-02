package reiz.miniecommerce.modules.products.core.entities;

import java.time.OffsetDateTime;
import java.util.UUID;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Domain model for ProductPhoto. Free of persistence concerns: no JPA annotations,
 * no framework types. Other aggregates are referenced by id.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(of = "id")
public class ProductPhoto {

    private UUID id;
    private UUID productId;
    private String url;
    private Short position;
    private boolean cover;

    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
}
