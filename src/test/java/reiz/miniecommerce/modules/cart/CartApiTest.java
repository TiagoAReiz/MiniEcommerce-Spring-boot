package reiz.miniecommerce.modules.cart;

import reiz.miniecommerce.modules.auth.core.entities.AuthenticatedPrincipal;
import reiz.miniecommerce.modules.auth.core.interfaces.repositories.AccessTokenIssuer;
import reiz.miniecommerce.modules.products.core.entities.Product;
import reiz.miniecommerce.modules.products.core.interfaces.repositories.ProductRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class CartApiTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private AccessTokenIssuer tokenIssuer;
    @Autowired private ProductRepository productRepository;

    private String bearer;
    private UUID productId;

    @BeforeEach
    void setUp() throws Exception {
        UUID userId = UUID.randomUUID();
        bearer = "Bearer " + tokenIssuer.issue(AuthenticatedPrincipal.builder()
                .id(userId)
                .email(userId + "@exemplo.com")
                .role(AuthenticatedPrincipal.Role.USER)
                .build()).getToken();

        productId = productRepository.save(Product.builder()
                .name("Caneca " + UUID.randomUUID())
                .price(new BigDecimal("49.90"))
                .stock(10)
                .active(true)
                .build()).getId();

        mockMvc.perform(delete("/cart").header("Authorization", bearer));
    }

    @Test
    void anEmptyCartIsTwoHundredNotFourOhFour() throws Exception {
        mockMvc.perform(get("/cart").header("Authorization", bearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lines").isEmpty())
                .andExpect(jsonPath("$.total").value(0))
                .andExpect(jsonPath("$.itemCount").value(0));
    }

    @Test
    void addingUsesTheServerSidePriceAndEmbedsTheProduct() throws Exception {
        mockMvc.perform(post("/cart/items")
                        .header("Authorization", bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(addItem(productId, 2)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lines.length()").value(1))
                .andExpect(jsonPath("$.lines[0].unitPrice").value(49.90))
                .andExpect(jsonPath("$.lines[0].quantity").value(2))
                .andExpect(jsonPath("$.lines[0].subtotal").value(99.80))
                .andExpect(jsonPath("$.lines[0].product.id").value(productId.toString()))
                .andExpect(jsonPath("$.total").value(99.80))
                .andExpect(jsonPath("$.itemCount").value(2));
    }

    @Test
    void addingTheSameProductTwiceSumsTheQuantity() throws Exception {
        add(productId, 2);
        add(productId, 3);

        mockMvc.perform(get("/cart").header("Authorization", bearer))
                .andExpect(jsonPath("$.lines.length()").value(1))
                .andExpect(jsonPath("$.lines[0].quantity").value(5));
    }

    @Test
    void refusesMoreUnitsThanTheStockHas() throws Exception {
        mockMvc.perform(post("/cart/items")
                        .header("Authorization", bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(addItem(productId, 11)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INSUFFICIENT_STOCK"));
    }

    @Test
    void countsWhatIsAlreadyInTheCartAgainstTheStock() throws Exception {
        add(productId, 8);

        // 8 + 5 is over the 10 available, even though 5 on its own would fit
        mockMvc.perform(post("/cart/items")
                        .header("Authorization", bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(addItem(productId, 5)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INSUFFICIENT_STOCK"));
    }

    @Test
    void patchSetsAnAbsoluteQuantityAndZeroRemovesTheLine() throws Exception {
        add(productId, 5);

        mockMvc.perform(patch("/cart/items/" + productId)
                        .header("Authorization", bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"quantity\":2}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lines[0].quantity").value(2));

        mockMvc.perform(patch("/cart/items/" + productId)
                        .header("Authorization", bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"quantity\":0}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lines").isEmpty());
    }

    @Test
    void removingSomethingThatIsNotThereIsStillFine() throws Exception {
        mockMvc.perform(delete("/cart/items/" + UUID.randomUUID()).header("Authorization", bearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lines").isEmpty());
    }

    @Test
    void refusesAProductThatWasRetired() throws Exception {
        Product retired = productRepository.save(Product.builder()
                .name("Aposentada " + UUID.randomUUID())
                .price(new BigDecimal("10.00"))
                .stock(5)
                .active(false)
                .build());

        mockMvc.perform(post("/cart/items")
                        .header("Authorization", bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(addItem(retired.getId(), 1)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INSUFFICIENT_STOCK"));
    }

    @Test
    void unknownProductIsFourOhFour() throws Exception {
        mockMvc.perform(post("/cart/items")
                        .header("Authorization", bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(addItem(UUID.randomUUID(), 1)))
                .andExpect(status().isNotFound());
    }

    @Test
    void cartNeedsAToken() throws Exception {
        mockMvc.perform(get("/cart")).andExpect(status().isUnauthorized());
    }

    // ---------- helpers ----------

    private void add(UUID id, int quantity) throws Exception {
        mockMvc.perform(post("/cart/items")
                        .header("Authorization", bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(addItem(id, quantity)))
                .andExpect(status().isOk());
    }

    private String addItem(UUID id, int quantity) {
        return """
                { "productId": "%s", "quantity": %d }
                """.formatted(id, quantity);
    }
}
