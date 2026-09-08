package reiz.miniecommerce.modules.owners.core.entities;

import java.time.OffsetDateTime;
import java.util.UUID;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Domain model for Owner. Free of persistence concerns: no JPA annotations,
 * no framework types. Other aggregates are referenced by id.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(of = "id")
public class Owner {

    private UUID id;
    private String googleSub;
    private String email;

    /**
     * Where orders ship from, and so the point every freight quote measures against.
     *
     * <p>Null until the operator sets it, which is also the switch for the whole feature:
     * a store without an origin charges no freight at all rather than guessing a price.
     */
    private String originZipCode;

    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
}
