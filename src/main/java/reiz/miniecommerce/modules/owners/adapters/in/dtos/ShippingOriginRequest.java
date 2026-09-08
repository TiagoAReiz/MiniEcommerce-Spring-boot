package reiz.miniecommerce.modules.owners.adapters.in.dtos;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/** The CEP freight is measured from. Accepts the hyphenated form operators actually type. */
public record ShippingOriginRequest(
        @NotBlank
        @Pattern(regexp = "[0-9]{5}-?[0-9]{3}", message = "CEP must have 8 digits, with or without the hyphen")
        String zipCode) {

    /** The column is 8 characters wide and the CEP gateway wants digits only. */
    public String normalizedZipCode() {
        return zipCode == null ? null : zipCode.replaceAll("[^0-9]", "");
    }
}
