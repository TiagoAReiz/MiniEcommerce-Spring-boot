package reiz.miniecommerce.modules.products.core.interfaces.repositories;

/**
 * Output port for binary storage of product images.
 *
 * <p>The core states what it needs — put these bytes somewhere and give me back a URL — and
 * says nothing about buckets, S3 or MinIO.
 */
public interface PhotoStorage {

    /**
     * @param originalFilename name as uploaded, used only to derive an extension
     * @param contentType      MIME type as declared by the client
     * @param content          the file itself
     * @return a URL that serves the stored object
     */
    String store(String originalFilename, String contentType, byte[] content);

    /** Removes a previously stored object. Missing objects are not an error. */
    void delete(String url);
}
