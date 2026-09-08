package reiz.miniecommerce.modules.products;

import reiz.miniecommerce.modules.address.core.entities.Address;
import reiz.miniecommerce.modules.address.core.interfaces.repositories.AddressRepository;
import reiz.miniecommerce.modules.auth.core.entities.AuthenticatedPrincipal;
import reiz.miniecommerce.modules.auth.core.interfaces.repositories.AccessTokenIssuer;
import reiz.miniecommerce.modules.owners.core.interfaces.repositories.OwnerRepository;
import reiz.miniecommerce.modules.products.core.entities.Product;
import reiz.miniecommerce.modules.products.core.interfaces.repositories.ProductRepository;
import reiz.miniecommerce.modules.users.core.entities.User;
import reiz.miniecommerce.modules.users.core.interfaces.repositories.UserRepository;
import reiz.miniecommerce.testsupport.Storefront;
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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * What selling the last unit does to a product.
 *
 * <p>The retirement happens inside the conditional {@code UPDATE} that takes the stock, not in
 * any service, so nothing above it would notice if it stopped working.
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        // unreachable CEP provider, zero-priced freight: this is about stock, not delivery
        "app.shipping.cep-base-url=http://127.0.0.1:1",
        "app.shipping.fallback-cost=0.00"
})
class ProductStockLifecycleTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private AccessTokenIssuer tokenIssuer;
    @Autowired private UserRepository userRepository;
    @Autowired private AddressRepository addressRepository;
    @Autowired private ProductRepository productRepository;
    @Autowired private OwnerRepository ownerRepository;

    private String bearer;
    private String ownerBearer;
    private UUID addressId;

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

        addressId = addressRepository.save(Address.builder()
                .userId(user.getId()).zipCode("01310100").street("Avenida Paulista")
                .neighborhood("Bela Vista").city("Sao Paulo").state("SP").country("BR")
                .primary(false).build()).getId();
    }

    @Test
    void sellingTheLastUnitRetiresTheProduct() throws Exception {
        UUID productId = productWithStock(1);

        mockMvc.perform(get("/products/" + productId))
                .andExpect(jsonPath("$.active").value(true))
                .andExpect(jsonPath("$.sellable").value(true));

        buy(productId, 1);

        // checkout reads the product before deducting, so this also proves the deduction
        // evicted the cache entry that read had just populated
        mockMvc.perform(get("/products/" + productId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stock").value(0))
                .andExpect(jsonPath("$.active").value(false))
                .andExpect(jsonPath("$.sellable").value(false));
    }

    @Test
    void aProductWithStockLeftStaysOnSale() throws Exception {
        UUID productId = productWithStock(3);

        buy(productId, 1);

        mockMvc.perform(get("/products/" + productId))
                .andExpect(jsonPath("$.stock").value(2))
                .andExpect(jsonPath("$.active").value(true))
                .andExpect(jsonPath("$.sellable").value(true));
    }

    @Test
    void restockingThroughUpdateBringsItBackOnSale() throws Exception {
        UUID productId = productWithStock(1);
        buy(productId, 1);

        mockMvc.perform(put("/products/" + productId)
                        .header("Authorization", ownerBearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "name": "Reposta %s", "price": 49.90, "stock": 7, "active": true }
                                """.formatted(UUID.randomUUID())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stock").value(7))
                .andExpect(jsonPath("$.active").value(true))
                .andExpect(jsonPath("$.sellable").value(true));
    }

    @Test
    void aRetiredProductCannotBeAddedToACart() throws Exception {
        UUID productId = productWithStock(1);
        buy(productId, 1);

        mockMvc.perform(post("/cart/items").header("Authorization", bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productId\":\"" + productId + "\",\"quantity\":1}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INSUFFICIENT_STOCK"));
    }

    // ---------- helpers ----------

    private UUID productWithStock(int stock) {
        return productRepository.save(Product.builder()
                .name("Caneca " + UUID.randomUUID())
                .price(new BigDecimal("49.90"))
                .stock(stock)
                .active(true)
                .build()).getId();
    }

    private void buy(UUID productId, int quantity) throws Exception {
        mockMvc.perform(post("/cart/items").header("Authorization", bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productId\":\"" + productId + "\",\"quantity\":" + quantity + "}"))
                .andExpect(status().isOk());

        mockMvc.perform(post("/orders").header("Authorization", bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"addressId\":\"" + addressId + "\"}"))
                .andExpect(status().isCreated());
    }

    private String bearerFor(UUID id, AuthenticatedPrincipal.Role role) {
        return "Bearer " + tokenIssuer.issue(AuthenticatedPrincipal.builder()
                .id(id).email(id + "@exemplo.com").role(role).build()).getToken();
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
