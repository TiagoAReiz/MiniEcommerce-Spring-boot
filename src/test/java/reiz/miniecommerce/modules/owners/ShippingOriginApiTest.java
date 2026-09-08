package reiz.miniecommerce.modules.owners;

import reiz.miniecommerce.modules.auth.core.entities.AuthenticatedPrincipal;
import reiz.miniecommerce.modules.auth.core.interfaces.repositories.AccessTokenIssuer;
import reiz.miniecommerce.modules.owners.core.entities.Owner;
import reiz.miniecommerce.modules.owners.core.interfaces.repositories.OwnerRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The one control the operator has over freight: the address it is measured from.
 *
 * <p>The endpoint is the only way the feature is ever turned on, so its access rule matters
 * as much as its behaviour — this is store configuration, not customer data, and a customer
 * who could write it would be repricing every order in the shop.
 */
@SpringBootTest
@AutoConfigureMockMvc
class ShippingOriginApiTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private AccessTokenIssuer tokenIssuer;
    @Autowired private OwnerRepository ownerRepository;

    private String ownerBearer;
    private String customerBearer;

    /**
     * The origin belongs to the storefront, which is one shared row that outlives the run.
     * Whatever it held before has to go back, or every later test that expects a shop with
     * no freight configured starts charging for delivery.
     */
    private String originalOrigin;

    @BeforeEach
    void setUp() {
        // the controller resolves the store rather than the caller, so any OWNER token works
        ownerBearer = bearerFor(UUID.randomUUID(), AuthenticatedPrincipal.Role.OWNER);
        customerBearer = bearerFor(UUID.randomUUID(), AuthenticatedPrincipal.Role.USER);

        originalOrigin = store().getOriginZipCode();
    }

    @AfterEach
    void restoreTheStoreOrigin() {
        Owner store = store();
        store.setOriginZipCode(originalOrigin);
        ownerRepository.save(store);
    }

    @Test
    void theOwnerSetsTheOriginAndReadsItBack() throws Exception {
        mockMvc.perform(putOrigin(ownerBearer, "01310100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.zipCode").value("01310100"));

        mockMvc.perform(get("/owners/origin").header("Authorization", ownerBearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.zipCode").value("01310100"));
    }

    /**
     * Operators type the CEP the way it is printed, with the hyphen. The column is eight
     * characters wide and the CEP gateway looks up digits only, so accepting the hyphenated
     * form without normalising it would either overflow the column or produce an origin that
     * never resolves — and an origin that never resolves prices every order at the
     * contingency rate without anything looking broken.
     */
    @Test
    void aHyphenatedCepIsStoredAsEightDigits() throws Exception {
        mockMvc.perform(putOrigin(ownerBearer, "01310-100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.zipCode").value("01310100"));

        assertThat(store().getOriginZipCode()).isEqualTo("01310100");
    }

    @Test
    void aMalformedCepIsRejected() throws Exception {
        mockMvc.perform(putOrigin(ownerBearer, "123"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));

        mockMvc.perform(putOrigin(ownerBearer, "abcdefgh"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));

        mockMvc.perform(putOrigin(ownerBearer, ""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));

        // nothing was written on the way to any of those 400s
        assertThat(store().getOriginZipCode()).isEqualTo(originalOrigin);
    }

    /**
     * A signed-in customer is still not an operator. Without this the shop's freight
     * settings would be writable by anyone who has ever logged in.
     */
    @Test
    void anAuthenticatedCustomerIsNotAllowedNearStoreSettings() throws Exception {
        mockMvc.perform(get("/owners/origin").header("Authorization", customerBearer))
                .andExpect(status().isForbidden());

        mockMvc.perform(putOrigin(customerBearer, "22071900"))
                .andExpect(status().isForbidden());

        assertThat(store().getOriginZipCode()).isEqualTo(originalOrigin);
    }

    @Test
    void anAnonymousCallerIsNotEvenTold() throws Exception {
        mockMvc.perform(get("/owners/origin"))
                .andExpect(status().isUnauthorized());
    }

    // ---------- helpers ----------

    private Owner store() {
        return ownerRepository.findStore().orElseThrow(
                () -> new IllegalStateException("No owner row: the V2 seed did not run"));
    }

    private org.springframework.test.web.servlet.RequestBuilder putOrigin(String bearer, String zipCode) {
        return put("/owners/origin")
                .header("Authorization", bearer)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"zipCode\":\"" + zipCode + "\"}");
    }

    private String bearerFor(UUID id, AuthenticatedPrincipal.Role role) {
        return "Bearer " + tokenIssuer.issue(AuthenticatedPrincipal.builder()
                .id(id).email(id + "@exemplo.com").role(role).build()).getToken();
    }
}
