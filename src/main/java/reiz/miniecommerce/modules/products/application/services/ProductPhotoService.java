package reiz.miniecommerce.modules.products.application.services;

import reiz.miniecommerce.modules.products.core.entities.ProductPhoto;
import reiz.miniecommerce.modules.products.core.exceptions.InvalidPhotoException;
import reiz.miniecommerce.modules.products.core.exceptions.ProductNotFoundException;
import reiz.miniecommerce.modules.products.core.interfaces.repositories.PhotoStorage;
import reiz.miniecommerce.modules.products.core.interfaces.repositories.ProductPhotoRepository;
import reiz.miniecommerce.modules.products.core.interfaces.repositories.ProductRepository;
import reiz.miniecommerce.modules.products.adapters.out.storage.StorageProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ProductPhotoService {

    private final ProductPhotoRepository photoRepository;
    private final ProductRepository productRepository;
    private final PhotoStorage photoStorage;
    private final StorageProperties storageProperties;

    @Transactional(readOnly = true)
    public List<ProductPhoto> listFor(UUID productId) {
        return photoRepository.findByProductId(productId);
    }

    /** Photos for a whole page of products, grouped by product, in one query. */
    @Transactional(readOnly = true)
    public Map<UUID, List<ProductPhoto>> listFor(Collection<UUID> productIds) {
        return photoRepository.findByProductIds(productIds).stream()
                .collect(Collectors.groupingBy(ProductPhoto::getProductId));
    }

    @Transactional
    public ProductPhoto upload(UUID productId, String filename, String contentType,
                               byte[] content, short position, boolean cover) {
        if (productRepository.findById(productId).isEmpty()) {
            throw new ProductNotFoundException(productId);
        }
        validate(contentType, content);

        String url = photoStorage.store(filename, contentType, content);

        if (cover) {
            photoRepository.clearCoverFor(productId);
        }
        return photoRepository.save(ProductPhoto.builder()
                .productId(productId)
                .url(url)
                .position(position)
                .cover(cover)
                .build());
    }

    @Transactional
    public ProductPhoto reorder(UUID productId, UUID photoId, Short position, Boolean cover) {
        ProductPhoto photo = photoRepository.findById(photoId)
                .filter(p -> productId.equals(p.getProductId()))
                .orElseThrow(() -> new InvalidPhotoException("Foto não encontrada neste produto"));

        if (position != null) {
            photo.setPosition(position);
        }
        if (Boolean.TRUE.equals(cover)) {
            photoRepository.clearCoverFor(productId);
            photo.setCover(true);
        } else if (Boolean.FALSE.equals(cover)) {
            photo.setCover(false);
        }
        return photoRepository.save(photo);
    }


    @Transactional
    public void delete(UUID productId, UUID photoId) {
        ProductPhoto photo = photoRepository.findById(photoId)
                .filter(p -> productId.equals(p.getProductId()))
                .orElseThrow(() -> new InvalidPhotoException("Foto não encontrada neste produto"));

        photoRepository.deleteById(photoId);
        photoStorage.delete(photo.getUrl());
    }


    private void validate(String contentType, byte[] content) {
        if (content == null || content.length == 0) {
            throw new InvalidPhotoException("Arquivo vazio");
        }
        if (content.length > storageProperties.getMaxFileSize()) {
            throw new InvalidPhotoException("Arquivo acima de "
                    + storageProperties.getMaxFileSize() / (1024 * 1024) + "MB");
        }
        if (contentType == null || !storageProperties.getAllowedContentTypes().contains(contentType)) {
            throw new InvalidPhotoException("Tipo não aceito. Use JPEG, PNG ou WebP");
        }
    }
}
