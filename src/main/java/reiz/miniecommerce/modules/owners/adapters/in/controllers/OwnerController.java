package reiz.miniecommerce.modules.owners.adapters.in.controllers;

import reiz.miniecommerce.modules.owners.adapters.in.dtos.ShippingOriginRequest;
import reiz.miniecommerce.modules.owners.adapters.in.dtos.ShippingOriginResponse;
import reiz.miniecommerce.modules.owners.core.entities.Owner;
import reiz.miniecommerce.modules.owners.core.interfaces.repositories.OwnerRepository;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Store-level settings, which today means the address freight is measured from.
 *
 * <p>The origin belongs to the storefront rather than to whoever is signed in — products
 * have no owner, so there is one origin however many operators hold a seat. That is why
 * this reads {@code findStore()} instead of the caller's own row.
 */
@RestController
@RequestMapping("/owners")
@RequiredArgsConstructor
@PreAuthorize("hasRole('OWNER')")
public class OwnerController {

    private final OwnerRepository ownerRepository;

    @GetMapping("/origin")
    public ShippingOriginResponse origin() {
        return ShippingOriginResponse.of(store());
    }

    /**
     * Changing this reprices future orders only. Quotes already frozen onto placed orders
     * stay as they were — the customer agreed to that amount.
     */
    @PutMapping("/origin")
    public ShippingOriginResponse setOrigin(@Valid @RequestBody ShippingOriginRequest request) {
        Owner store = store();
        store.setOriginZipCode(request.normalizedZipCode());
        return ShippingOriginResponse.of(ownerRepository.save(store));
    }

    /**
     * The row is seeded by migration, so its absence is a broken deployment rather than
     * anything a caller did — which is why this is a 500 and not a 404.
     */
    private Owner store() {
        return ownerRepository.findStore()
                .orElseThrow(() -> new IllegalStateException("No owner row: the store seed is missing"));
    }
}
