package reiz.miniecommerce.modules.products.adapters.out.storage;

import reiz.miniecommerce.modules.products.core.interfaces.repositories.PhotoStorage;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.NoSuchBucketException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.util.Locale;
import java.util.UUID;

/**
 * Stores product images in an S3-compatible bucket — MinIO in development.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class S3PhotoStorage implements PhotoStorage {

    private final S3Client s3Client;
    private final StorageProperties properties;

    /** Creates the bucket on first boot so a fresh environment needs no manual setup. */
    @PostConstruct
    void ensureBucketExists() {
        try {
            s3Client.headBucket(b -> b.bucket(properties.getBucket()));
        } catch (NoSuchBucketException e) {
            log.info("Creating storage bucket {}", properties.getBucket());
            s3Client.createBucket(CreateBucketRequest.builder()
                    .bucket(properties.getBucket())
                    .build());
        }
    }

    @Override
    public String store(String originalFilename, String contentType, byte[] content) {
        // The stored name is generated, never the uploaded one: a client-supplied name can
        // carry path separators, collide with an existing object, or overwrite another
        // product's image.
        String key = UUID.randomUUID() + extensionOf(originalFilename);

        s3Client.putObject(PutObjectRequest.builder()
                        .bucket(properties.getBucket())
                        .key(key)
                        .contentType(contentType)
                        .build(),
                RequestBody.fromBytes(content));

        return "%s/%s/%s".formatted(properties.getPublicUrl(), properties.getBucket(), key);
    }

    @Override
    public void delete(String url) {
        String key = url.substring(url.lastIndexOf('/') + 1);
        s3Client.deleteObject(DeleteObjectRequest.builder()
                .bucket(properties.getBucket())
                .key(key)
                .build());
    }

    private String extensionOf(String filename) {
        if (filename == null) {
            return "";
        }
        int dot = filename.lastIndexOf('.');
        if (dot < 0 || dot == filename.length() - 1) {
            return "";
        }
        String extension = filename.substring(dot).toLowerCase(Locale.ROOT);
        return extension.matches("\\.[a-z0-9]{1,5}") ? extension : "";
    }
}
