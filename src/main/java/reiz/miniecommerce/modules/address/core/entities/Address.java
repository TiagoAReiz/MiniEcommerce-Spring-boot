package reiz.miniecommerce.modules.address.core.entities;

import java.time.OffsetDateTime;
import java.util.UUID;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Domain model for Address. Free of persistence concerns: no JPA annotations,
 * no framework types. Other aggregates are referenced by id.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(of = "id")
public class Address {

    private UUID id;
    private UUID userId;
    private boolean primary;
    private String zipCode;
    private String street;
    private String streetNumber;
    private String neighborhood;
    private String city;
    private String state;
    private String country;

    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
}
