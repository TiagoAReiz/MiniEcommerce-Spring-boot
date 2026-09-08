package reiz.miniecommerce.modules.owners.adapters.in.dtos;

import reiz.miniecommerce.modules.owners.core.entities.Owner;

/** {@code zipCode} is null while the store has not configured an origin — freight is off. */
public record ShippingOriginResponse(String zipCode) {

    public static ShippingOriginResponse of(Owner owner) {
        return new ShippingOriginResponse(owner.getOriginZipCode());
    }
}
