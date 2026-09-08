package reiz.miniecommerce.modules.products.adapters.out.repositories.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import reiz.miniecommerce.modules.products.core.entities.ProductHighlight;
import reiz.miniecommerce.modules.products.core.entities.ProductSpec;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.annotations.UuidGenerator;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "products")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(of = "id")
public class ProductJpaEntity {

    @Id
    @GeneratedValue
    @UuidGenerator(style = UuidGenerator.Style.TIME)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "name", nullable = false, length = 255)
    private String name;

    @Column(name = "description", columnDefinition = "text")
    private String description;

    @Column(name = "price", nullable = false, precision = 12, scale = 2)
    private BigDecimal price;

    @Column(name = "stock", nullable = false)
    private Integer stock;

    @Column(name = "active", nullable = false)
    private boolean active;

    @Column(name = "category", length = 60)
    private String category;

    /**
     * Stored as JSONB and mapped whole. These are display data read with the product and
     * never queried into, and their order is the operator's — a JSON array keeps that without
     * a child table and a position column on every read. See V6__product_catalog_data.sql.
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "highlights", nullable = false, columnDefinition = "jsonb")
    private List<ProductHighlight> highlights;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "specs", nullable = false, columnDefinition = "jsonb")
    private List<ProductSpec> specs;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;
}
