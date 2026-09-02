package reiz.miniecommerce.modules.products.adapters.in.dtos;

import jakarta.validation.constraints.Min;

/**
 * Both fields optional. Changing the URL is not offered: replacing an image means deleting
 * the photo and uploading another, so the stored object never outlives its row.
 */
public record UpdatePhotoRequest(@Min(0) Short position, Boolean isCover) {
}
