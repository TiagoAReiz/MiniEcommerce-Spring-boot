package reiz.miniecommerce.modules.products.adapters.in.controllers;

import reiz.miniecommerce.modules.products.adapters.in.dtos.ProductPhotoResponse;
import reiz.miniecommerce.modules.products.adapters.in.dtos.UpdatePhotoRequest;
import reiz.miniecommerce.modules.products.application.services.ProductPhotoService;
import reiz.miniecommerce.modules.products.core.exceptions.InvalidPhotoException;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/products/{productId}/photos")
@RequiredArgsConstructor
public class ProductPhotoController {

    private final ProductPhotoService photoService;

    @GetMapping
    public List<ProductPhotoResponse> list(@PathVariable UUID productId) {
        return photoService.listFor(productId).stream()
                .map(ProductPhotoResponse::from)
                .toList();
    }

    @PostMapping(consumes = "multipart/form-data")
    @PreAuthorize("hasRole('OWNER')")
    @ResponseStatus(HttpStatus.CREATED)
    public ProductPhotoResponse upload(@PathVariable UUID productId,
                                       @RequestParam("file") MultipartFile file,
                                       @RequestParam(defaultValue = "0") short position,
                                       @RequestParam(defaultValue = "false") boolean isCover) {
        try {
            return ProductPhotoResponse.from(photoService.upload(
                    productId, file.getOriginalFilename(), file.getContentType(),
                    file.getBytes(), position, isCover));
        } catch (IOException e) {
            throw new InvalidPhotoException("Nao foi possivel ler o arquivo enviado");
        }
    }

    @PatchMapping("/{photoId}")
    @PreAuthorize("hasRole('OWNER')")
    public ProductPhotoResponse update(@PathVariable UUID productId,
                                       @PathVariable UUID photoId,
                                       @Valid @RequestBody UpdatePhotoRequest request) {
        return ProductPhotoResponse.from(
                photoService.reorder(productId, photoId, request.position(), request.isCover()));
    }

    @DeleteMapping("/{photoId}")
    @PreAuthorize("hasRole('OWNER')")
    public ResponseEntity<Void> delete(@PathVariable UUID productId, @PathVariable UUID photoId) {
        photoService.delete(productId, photoId);
        return ResponseEntity.noContent().build();
    }
}
