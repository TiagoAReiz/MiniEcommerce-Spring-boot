package reiz.miniecommerce.modules.products.adapters.in.dtos;

import reiz.miniecommerce.modules.products.core.entities.ProductPhoto;

import java.util.UUID;

public record ProductPhotoResponse(UUID id, String url, Short position, boolean isCover) {

    public static ProductPhotoResponse from(ProductPhoto photo) {
        return new ProductPhotoResponse(photo.getId(), photo.getUrl(), photo.getPosition(), photo.isCover());
    }

    public boolean isCover() {
        return isCover;
    }
}
