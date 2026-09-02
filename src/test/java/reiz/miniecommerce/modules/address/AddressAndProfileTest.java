package reiz.miniecommerce.modules.address;

import reiz.miniecommerce.modules.auth.core.entities.AuthenticatedPrincipal;
import reiz.miniecommerce.modules.auth.core.interfaces.repositories.AccessTokenIssuer;
import reiz.miniecommerce.modules.address.core.interfaces.repositories.AddressRepository;
import reiz.miniecommerce.modules.users.core.entities.User;
import reiz.miniecommerce.modules.users.core.interfaces.repositories.UserRepository;
import reiz.miniecommerce.testsupport.Cpfs;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class AddressAndProfileTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private AccessTokenIssuer tokenIssuer;
    @Autowired private UserRepository userRepository;
    @Autowired private AddressRepository addressRepository;

    private String bearer;
    private UUID userId;
    private String cpf;

    @BeforeEach
    void signIn() {
        User user = userRepository.save(User.fromGoogleProfile(
                "google-sub-" + UUID.randomUUID(),
                "Tiago",
                UUID.randomUUID() + "@exemplo.com",
                "https://exemplo.com/foto.jpg"));
        userId = user.getId();

        // unique per run, and a real CPF: uq_users_cpf is global, so a literal would collide
        // with the row left behind by the previous execution, and the check digits are
        // enforced now, so arbitrary digits no longer get through
        cpf = Cpfs.random();

        bearer = "Bearer " + tokenIssuer.issue(AuthenticatedPrincipal.builder()
                .id(userId)
                .email(user.getEmail())
                .role(AuthenticatedPrincipal.Role.USER)
                .build()).getToken();
    }

    @Test
    void profileStartsIncompleteAndBecomesCheckoutReadyOnceCpfAndPhoneArrive() throws Exception {
        mockMvc.perform(get("/users/me").header("Authorization", bearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Tiago"))
                .andExpect(jsonPath("$.canCheckout").value(false))
                .andExpect(jsonPath("$.googleSub").doesNotExist());

        mockMvc.perform(patch("/users/me")
                        .header("Authorization", bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"cpf\":\"" + cpf + "\",\"phone\":\"+5511999999999\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.canCheckout").value(true));
    }

    @Test
    void rejectsACpfThatCarriesPunctuation() throws Exception {
        // the column is VARCHAR(11): a formatted value would be truncated on the way in
        patchCpf("123.456.789-09")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.errors[0].field").value("cpf"))
                .andExpect(jsonPath("$.errors[0].message").value("deve ter 11 dígitos, sem pontuação"));
    }

    @Test
    void rejectsElevenDigitsWhoseCheckDigitsDoNotAddUp() throws Exception {
        // right shape, impossible number — what the old format-only rule let through
        patchCpf("12345678901")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("cpf"))
                .andExpect(jsonPath("$.errors[0].message").value("CPF inválido"));
    }

    @Test
    void rejectsARepeatedDigitSequence() throws Exception {
        // 11111111111 satisfies the check-digit arithmetic, so only the repeated-digit rule
        // catches it
        patchCpf("11111111111")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("cpf"));
    }

    @Test
    void aCpfAlreadyOnAnotherAccountIsRejected() throws Exception {
        patchCpf(cpf).andExpect(status().isOk());

        User other = userRepository.save(User.fromGoogleProfile(
                "google-sub-" + UUID.randomUUID(), "Outro",
                UUID.randomUUID() + "@exemplo.com", null));
        String otherBearer = "Bearer " + tokenIssuer.issue(AuthenticatedPrincipal.builder()
                .id(other.getId()).email(other.getEmail())
                .role(AuthenticatedPrincipal.Role.USER).build()).getToken();

        mockMvc.perform(patch("/users/me")
                        .header("Authorization", otherBearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"cpf\":\"" + cpf + "\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CPF_ALREADY_USED"));
    }

    private org.springframework.test.web.servlet.ResultActions patchCpf(String value) throws Exception {
        return mockMvc.perform(patch("/users/me")
                .header("Authorization", bearer)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"cpf\":\"" + value + "\"}"));
    }

    @Test
    void promotingANewPrimaryAddressDemotesThePreviousOne() throws Exception {
        create("01310100", "Avenida Paulista", true);
        create("22071900", "Avenida Atlântica", true);

        assertThat(addressRepository.findByUserId(userId)).hasSize(2);

        var primary = addressRepository.findPrimaryByUserId(userId).orElseThrow();
        assertThat(primary.getStreet()).isEqualTo("Avenida Atlântica");

        // and the list puts the primary first
        mockMvc.perform(get("/users/me/addresses").header("Authorization", bearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].street").value("Avenida Atlântica"))
                .andExpect(jsonPath("$[0].isPrimary").value(true))
                .andExpect(jsonPath("$[1].isPrimary").value(false));
    }

    @Test
    void anotherUsersAddressLooksLikeItDoesNotExist() throws Exception {
        create("01310100", "Avenida Paulista", true);
        UUID addressId = addressRepository.findByUserId(userId).get(0).getId();

        User stranger = userRepository.save(User.fromGoogleProfile(
                "google-sub-" + UUID.randomUUID(), "Outro",
                UUID.randomUUID() + "@exemplo.com", null));
        String strangerBearer = "Bearer " + tokenIssuer.issue(AuthenticatedPrincipal.builder()
                .id(stranger.getId())
                .email(stranger.getEmail())
                .role(AuthenticatedPrincipal.Role.USER)
                .build()).getToken();

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .delete("/users/me/addresses/" + addressId)
                        .header("Authorization", strangerBearer))
                .andExpect(status().isNotFound());
    }

    @Test
    void addressesRequireAToken() throws Exception {
        mockMvc.perform(get("/users/me/addresses"))
                .andExpect(status().isUnauthorized());
    }

    private void create(String zip, String street, boolean primary) throws Exception {
        mockMvc.perform(post("/users/me/addresses")
                        .header("Authorization", bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "zipCode": "%s",
                                  "street": "%s",
                                  "streetNumber": "100",
                                  "neighborhood": "Centro",
                                  "city": "São Paulo",
                                  "state": "SP",
                                  "isPrimary": %s
                                }
                                """.formatted(zip, street, primary)))
                .andExpect(status().isCreated());
    }
}
