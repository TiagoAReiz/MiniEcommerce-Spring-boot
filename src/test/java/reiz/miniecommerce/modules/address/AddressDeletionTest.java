package reiz.miniecommerce.modules.address;

import reiz.miniecommerce.modules.auth.core.entities.AuthenticatedPrincipal;
import reiz.miniecommerce.modules.auth.core.interfaces.repositories.AccessTokenIssuer;
import reiz.miniecommerce.modules.owners.core.interfaces.repositories.OwnerRepository;
import reiz.miniecommerce.modules.products.core.entities.Product;
import reiz.miniecommerce.modules.products.core.interfaces.repositories.ProductRepository;
import reiz.miniecommerce.modules.users.core.entities.User;
import reiz.miniecommerce.modules.users.core.interfaces.repositories.UserRepository;
import reiz.miniecommerce.testsupport.Storefront;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Removing an address, and the one case where the database refuses.
 *
 * <p>{@code shipments.address_id} is a RESTRICT foreign key, so an address that already
 * carried a parcel cannot be deleted — the delivery record would lose where it went.
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        // a dead CEP endpoint keeps the checkout here off the public API, and freight at zero
        // leaves the numbers this test cares about untouched
        "app.shipping.cep-base-url=http://127.0.0.1:1",
        "app.shipping.fallback-cost=0.00"
})
class AddressDeletionTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private AccessTokenIssuer tokenIssuer;
    @Autowired private UserRepository userRepository;
    @Autowired private ProductRepository productRepository;
    @Autowired private OwnerRepository ownerRepository;

    private String bearer;
    private String ownerBearer;

    @BeforeEach
    void signIn() {
        Storefront.sellsWithFreight(ownerRepository);

        User user = userRepository.save(User.fromGoogleProfile(
                "sub-" + UUID.randomUUID(), "Tiago", UUID.randomUUID() + "@exemplo.com", null));
        user.setCpf(randomCpf());
        user.setPhone("+5511999999999");
        user = userRepository.save(user);

        bearer = bearerFor(user.getId(), AuthenticatedPrincipal.Role.USER);
        ownerBearer = bearerFor(UUID.randomUUID(), AuthenticatedPrincipal.Role.OWNER);
    }

    @Test
    void anAddressThatNeverShippedAnythingIsRemoved() throws Exception {
        String addressId = createAddress();

        mockMvc.perform(delete("/users/me/addresses/" + addressId).header("Authorization", bearer))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/users/me/addresses").header("Authorization", bearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void anAddressAlreadyUsedByAShipmentCannotBeRemoved() throws Exception {
        String addressId = createAddress();
        shipAnOrderTo(addressId);

        mockMvc.perform(delete("/users/me/addresses/" + addressId).header("Authorization", bearer))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ADDRESS_IN_USE"));

        // and it is still there afterwards, not half-deleted
        mockMvc.perform(get("/users/me/addresses").header("Authorization", bearer))
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(addressId));
    }

    // ---------- helpers ----------

    private String bearerFor(UUID id, AuthenticatedPrincipal.Role role) {
        return "Bearer " + tokenIssuer.issue(AuthenticatedPrincipal.builder()
                .id(id).email(id + "@exemplo.com").role(role).build()).getToken();
    }

    private String createAddress() throws Exception {
        return JsonPath.read(mockMvc.perform(post("/users/me/addresses")
                                .header("Authorization", bearer)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {
                                          "zipCode": "01310100",
                                          "street": "Avenida Paulista",
                                          "streetNumber": "1578",
                                          "neighborhood": "Bela Vista",
                                          "city": "Sao Paulo",
                                          "state": "SP",
                                          "isPrimary": true
                                        }
                                        """))
                        .andExpect(status().isCreated())
                        .andReturn().getResponse().getContentAsString(), "$.id");
    }

    /** Buys something, pays it and dispatches it, so the address ends up on a shipment. */
    private void shipAnOrderTo(String addressId) throws Exception {
        UUID productId = productRepository.save(Product.builder()
                .name("Caneca " + UUID.randomUUID())
                .price(new BigDecimal("49.90")).stock(5).active(true).build()).getId();

        mockMvc.perform(post("/cart/items").header("Authorization", bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productId\":\"" + productId + "\",\"quantity\":1}"))
                .andExpect(status().isOk());

        String orderId = JsonPath.read(mockMvc.perform(post("/orders")
                                .header("Authorization", bearer)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"addressId\":\"" + addressId + "\"}"))
                        .andExpect(status().isCreated())
                        .andReturn().getResponse().getContentAsString(), "$.id");

        mockMvc.perform(patch("/orders/" + orderId + "/status")
                        .header("Authorization", ownerBearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"PAID\"}"))
                .andExpect(status().isOk());

        mockMvc.perform(post("/orders/" + orderId + "/shipment")
                        .header("Authorization", ownerBearer)
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isCreated());
    }

    /**
     * Real check digits, so the value survives wherever CPF happens to be validated. A random
     * eleven-digit string would not, and {@code uq_users_cpf} rules out a fixed one.
     */
    private static String randomCpf() {
        int[] digits = new int[11];
        do {
            for (int i = 0; i < 9; i++) {
                digits[i] = ThreadLocalRandom.current().nextInt(10);
            }
        } while (allTheSame(digits));

        digits[9] = checkDigit(digits, 9, 10);
        digits[10] = checkDigit(digits, 10, 11);

        StringBuilder cpf = new StringBuilder(11);
        for (int digit : digits) {
            cpf.append(digit);
        }
        return cpf.toString();
    }

    private static boolean allTheSame(int[] digits) {
        for (int i = 1; i < 9; i++) {
            if (digits[i] != digits[0]) {
                return false;
            }
        }
        return true;
    }

    private static int checkDigit(int[] digits, int length, int startWeight) {
        int sum = 0;
        for (int i = 0; i < length; i++) {
            sum += digits[i] * (startWeight - i);
        }
        int remainder = sum % 11;
        return remainder < 2 ? 0 : 11 - remainder;
    }
}
