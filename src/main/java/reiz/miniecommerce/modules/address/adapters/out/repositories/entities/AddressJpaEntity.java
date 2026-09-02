package reiz.miniecommerce.modules.address.adapters.out.repositories.entities;

import reiz.miniecommerce.modules.users.adapters.out.repositories.entities.UserJpaEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.annotations.UuidGenerator;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "addresses")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(of = "id")
public class AddressJpaEntity {

    @Id
    @GeneratedValue
    @UuidGenerator(style = UuidGenerator.Style.TIME)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private UserJpaEntity user;

    @Column(name = "is_primary", nullable = false)
    private boolean primary;

    @Column(name = "zip_code", nullable = false, length = 8)
    private String zipCode;

    @Column(name = "street", nullable = false, length = 255)
    private String street;

    @Column(name = "street_number", length = 10)
    private String streetNumber;

    @Column(name = "neighborhood", nullable = false, length = 255)
    private String neighborhood;

    @Column(name = "city", nullable = false, length = 255)
    private String city;

    @Column(name = "state", nullable = false, length = 2)
    private String state;

    @Column(name = "country", nullable = false, length = 2)
    private String country;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;
}
