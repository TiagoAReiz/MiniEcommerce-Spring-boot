package reiz.miniecommerce.modules.products.adapters.out.storage;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.Set;

@Getter
@Setter
@ConfigurationProperties(prefix = "app.storage")
public class StorageProperties {

    /** S3 endpoint. MinIO locally; leave empty to use the real AWS endpoint. */
    private String endpoint = "http://localhost:9000";

    /** Base URL objects are served from. Usually the endpoint, or a CDN in front of it. */
    private String publicUrl = "http://localhost:9000";

    private String region = "us-east-1";
    private String bucket = "product-photos";
    private String accessKey = "minioadmin";
    private String secretKey = "minioadmin";

    /** MinIO needs bucket-in-path URLs; real S3 uses bucket-as-subdomain. */
    private boolean pathStyleAccess = true;

    private long maxFileSize = 5 * 1024 * 1024;

    private Set<String> allowedContentTypes = Set.of("image/jpeg", "image/png", "image/webp");
}
