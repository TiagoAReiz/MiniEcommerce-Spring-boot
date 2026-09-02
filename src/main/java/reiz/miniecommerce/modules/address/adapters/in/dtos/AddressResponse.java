package reiz.miniecommerce.modules.address.adapters.in.dtos;

import reiz.miniecommerce.modules.address.core.entities.Address;

import java.time.OffsetDateTime;
import java.util.UUID;

public record AddressResponse(
        UUID id,
        String zipCode,
        String street,
        String streetNumber,
        String neighborhood,
        String city,
        String state,
        String country,
        boolean isPrimary,
        OffsetDateTime createdAt) {

    public static AddressResponse from(Address address) {
        return new AddressResponse(
                address.getId(),
                address.getZipCode(),
                address.getStreet(),
                address.getStreetNumber(),
                address.getNeighborhood(),
                address.getCity(),
                address.getState(),
                address.getCountry(),
                address.isPrimary(),
                address.getCreatedAt());
    }
}
