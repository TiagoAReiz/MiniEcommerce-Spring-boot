package reiz.miniecommerce.modules.address.adapters.in.controllers;

import reiz.miniecommerce.modules.address.adapters.in.dtos.AddressRequest;
import reiz.miniecommerce.modules.address.adapters.in.dtos.AddressResponse;
import reiz.miniecommerce.modules.address.application.services.AddressService;
import reiz.miniecommerce.modules.auth.application.services.CurrentUserProvider;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/users/me/addresses")
@RequiredArgsConstructor
public class AddressController {

    private final AddressService addressService;
    private final CurrentUserProvider currentUser;

    @GetMapping
    public List<AddressResponse> list() {
        return addressService.listFor(currentUser.requireId()).stream()
                .map(AddressResponse::from)
                .toList();
    }

    @PostMapping
    public ResponseEntity<AddressResponse> create(@Valid @RequestBody AddressRequest request,
                                                  UriComponentsBuilder uri) {
        AddressResponse created = AddressResponse.from(
                addressService.create(currentUser.requireId(), request.toDomain()));

        return ResponseEntity
                .created(uri.path("/users/me/addresses/{id}").build(created.id()))
                .body(created);
    }

    @PutMapping("/{id}")
    public AddressResponse update(@PathVariable UUID id, @Valid @RequestBody AddressRequest request) {
        return AddressResponse.from(
                addressService.update(currentUser.requireId(), id, request.toDomain()));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        addressService.delete(currentUser.requireId(), id);
        return ResponseEntity.noContent().build();
    }
}
