package reiz.miniecommerce.modules.address.adapters.in.dtos;

import reiz.miniecommerce.modules.address.core.entities.Address;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * An address as the front end sends it. {@code userId} is absent on purpose: whose address
 * this is comes from the token, never from the body.
 */
public record AddressRequest(
        @NotBlank
        @Pattern(regexp = "\\d{8}", message = "deve ter 8 dígitos, sem hífen")
        String zipCode,

        @NotBlank String street,

        @Size(max = 10) String streetNumber,

        @NotBlank String neighborhood,

        @NotBlank String city,

        @NotBlank
        @Pattern(regexp = "[A-Z]{2}", message = "deve ser a sigla da UF, em maiúsculas")
        String state,

        @Pattern(regexp = "[A-Z]{2}", message = "deve ser o código ISO do país")
        String country,

        boolean isPrimary) {

    public Address toDomain() {
        return Address.builder()
                .zipCode(zipCode)
                .street(street)
                .streetNumber(streetNumber)
                .neighborhood(neighborhood)
                .city(city)
                .state(state)
                .country(country == null ? "BR" : country)
                .primary(isPrimary)
                .build();
    }
}
