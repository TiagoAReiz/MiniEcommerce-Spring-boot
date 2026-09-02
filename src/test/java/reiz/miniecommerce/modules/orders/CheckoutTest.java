package reiz.miniecommerce.modules.orders;

import reiz.miniecommerce.modules.address.core.entities.Address;
import reiz.miniecommerce.modules.address.core.interfaces.repositories.AddressRepository;
import reiz.miniecommerce.modules.auth.core.entities.AuthenticatedPrincipal;
import reiz.miniecommerce.modules.auth.core.interfaces.repositories.AccessTokenIssuer;
import reiz.miniecommerce.modules.cart.core.interfaces.repositories.CartRepository;
import reiz.miniecommerce.modules.products.core.entities.Product;
import reiz.miniecommerce.modules.products.core.interfaces.repositories.ProductRepository;
import reiz.miniecommerce.modules.users.core.entities.User;
import reiz.miniecommerce.modules.users.core.interfaces.repositories.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class CheckoutTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private AccessTokenIssuer tokenIssuer;
    @Autowired private UserRepository userRepository;
    @Autowired private AddressRepository addressRepository;
    @Autowired private ProductRepository productRepository;
    @Autowired private CartRepository cartRepository;

    private UUID userId;
    private String bearer;
    private String ownerBearer;
    private UUID addressId;
    private UUID productId;

    @BeforeEach
    void setUp() {
        User user = userRepository.save(User.fromGoogleProfile(
                "sub-" + UUID.randomUUID(), "Tiago", UUID.randomUUID() + "@exemplo.com", null));
        user.setCpf(String.valueOf(System.nanoTime()).substring(0, 11));
        user.setPhone("+5511999999999");
        user = userRepository.save(user);
        userId = user.getId();

        bearer = bearerFor(userId, AuthenticatedPrincipal.Role.USER);
        ownerBearer = bearerFor(UUID.randomUUID(), AuthenticatedPrincipal.Role.OWNER);

        addressId = addressRepository.save(Address.builder()
                .userId(userId).zipCode("01310100").street("Avenida Paulista")
                .streetNumber("1578").neighborhood("Bela Vista").city("São Paulo")
                .state("SP").country("BR").primary(false)
                .build()).getId();

        productId = productRepository.save(Product.builder()
                .name("Caneca " + UUID.randomUUID())
                .price(new BigDecimal("49.90")).stock(10).active(true)
                .build()).getId();

        cartRepository.deleteByUserId(userId);
    }

    @Test
    void checkoutTurnsTheCartIntoAnOrderAndTakesTheStock() throws Exception {
        addToCart(productId, 2);

        mockMvc.perform(checkout(addressId))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.total").value(99.80))
                .andExpect(jsonPath("$.itemCount").value(2))
                .andExpect(jsonPath("$.addressId").value(addressId.toString()))
                .andExpect(jsonPath("$.items[0].unitPrice").value(49.90));

        assertThat(productRepository.findById(productId).orElseThrow().getStock()).isEqualTo(8);
        assertThat(cartRepository.findByUserId(userId)).isEmpty();
    }

    @Test
    void anEmptyCartCannotBeCheckedOut() throws Exception {
        mockMvc.perform(checkout(addressId))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("EMPTY_CART"));
    }

    @Test
    void aProfileWithoutCpfIsBlocked() throws Exception {
        User incomplete = userRepository.save(User.fromGoogleProfile(
                "sub-" + UUID.randomUUID(), "Sem CPF", UUID.randomUUID() + "@exemplo.com", null));
        String otherBearer = bearerFor(incomplete.getId(), AuthenticatedPrincipal.Role.USER);

        UUID otherAddress = addressRepository.save(Address.builder()
                .userId(incomplete.getId()).zipCode("01310100").street("Rua X")
                .neighborhood("Centro").city("São Paulo").state("SP").country("BR")
                .primary(false).build()).getId();

        mockMvc.perform(post("/cart/items")
                        .header("Authorization", otherBearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productId\":\"" + productId + "\",\"quantity\":1}"))
                .andExpect(status().isOk());

        mockMvc.perform(post("/orders")
                        .header("Authorization", otherBearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"addressId\":\"" + otherAddress + "\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("CHECKOUT_BLOCKED"));
    }

    @Test
    void refusesWhenThePriceMovedWhileTheCartSat() throws Exception {
        addToCart(productId, 1);

        Product product = productRepository.findById(productId).orElseThrow();
        product.setPrice(new BigDecimal("59.90"));
        productRepository.save(product);

        mockMvc.perform(checkout(addressId))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PRICE_CHANGED"));
    }

    @Test
    void refusesWhenTheStockRanOutAfterTheItemWasAdded() throws Exception {
        addToCart(productId, 5);

        Product product = productRepository.findById(productId).orElseThrow();
        product.setStock(2);
        productRepository.save(product);

        mockMvc.perform(checkout(addressId))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INSUFFICIENT_STOCK"));
    }

    @Test
    void theCartSurvivesAFailedCheckout() throws Exception {
        addToCart(productId, 1);

        Product product = productRepository.findById(productId).orElseThrow();
        product.setPrice(new BigDecimal("59.90"));
        productRepository.save(product);

        mockMvc.perform(checkout(addressId)).andExpect(status().isConflict());

        // the rollback must not have taken the cart with it
        assertThat(cartRepository.findByUserId(userId)).isPresent();
    }

    @Test
    void anotherUsersAddressCannotBeUsed() throws Exception {
        addToCart(productId, 1);

        User stranger = userRepository.save(User.fromGoogleProfile(
                "sub-" + UUID.randomUUID(), "Outro", UUID.randomUUID() + "@exemplo.com", null));
        UUID strangerAddress = addressRepository.save(Address.builder()
                .userId(stranger.getId()).zipCode("22071900").street("Avenida Atlântica")
                .neighborhood("Copacabana").city("Rio de Janeiro").state("RJ").country("BR")
                .primary(false).build()).getId();

        mockMvc.perform(checkout(strangerAddress))
                .andExpect(status().isNotFound());
    }

    @Test
    void ownerAdvancesTheStatusButNotOutOfOrder() throws Exception {
        addToCart(productId, 1);
        String orderId = com.jayway.jsonpath.JsonPath.read(
                mockMvc.perform(checkout(addressId)).andReturn().getResponse().getContentAsString(), "$.id");

        // PENDING cannot jump straight to SHIPPED
        mockMvc.perform(patch("/orders/" + orderId + "/status")
                        .header("Authorization", ownerBearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"SHIPPED\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVALID_STATUS_TRANSITION"));

        mockMvc.perform(patch("/orders/" + orderId + "/status")
                        .header("Authorization", ownerBearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"PAID\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PAID"));
    }

    @Test
    void cancellingPutsTheStockBack() throws Exception {
        addToCart(productId, 3);
        String orderId = com.jayway.jsonpath.JsonPath.read(
                mockMvc.perform(checkout(addressId)).andReturn().getResponse().getContentAsString(), "$.id");

        assertThat(productRepository.findById(productId).orElseThrow().getStock()).isEqualTo(7);

        mockMvc.perform(patch("/orders/" + orderId + "/status")
                        .header("Authorization", ownerBearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"CANCELLED\"}"))
                .andExpect(status().isOk());

        assertThat(productRepository.findById(productId).orElseThrow().getStock()).isEqualTo(10);
    }

    @Test
    void customersCannotChangeStatusAndCannotSeeEachOthersOrders() throws Exception {
        addToCart(productId, 1);
        String orderId = com.jayway.jsonpath.JsonPath.read(
                mockMvc.perform(checkout(addressId)).andReturn().getResponse().getContentAsString(), "$.id");

        mockMvc.perform(patch("/orders/" + orderId + "/status")
                        .header("Authorization", bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"PAID\"}"))
                .andExpect(status().isForbidden());

        String strangerBearer = bearerFor(UUID.randomUUID(), AuthenticatedPrincipal.Role.USER);
        mockMvc.perform(get("/orders/" + orderId).header("Authorization", strangerBearer))
                .andExpect(status().isNotFound());

        // the owner sees everything
        mockMvc.perform(get("/orders/" + orderId).header("Authorization", ownerBearer))
                .andExpect(status().isOk());
    }

    @Test
    void myOrdersIsPaged() throws Exception {
        addToCart(productId, 1);
        mockMvc.perform(checkout(addressId)).andExpect(status().isCreated());

        mockMvc.perform(get("/orders").header("Authorization", bearer).param("size", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.size").value(5))
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    // ---------- helpers ----------

    private String bearerFor(UUID id, AuthenticatedPrincipal.Role role) {
        return "Bearer " + tokenIssuer.issue(AuthenticatedPrincipal.builder()
                .id(id).email(id + "@exemplo.com").role(role).build()).getToken();
    }

    private void addToCart(UUID product, int quantity) throws Exception {
        mockMvc.perform(post("/cart/items")
                        .header("Authorization", bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productId\":\"" + product + "\",\"quantity\":" + quantity + "}"))
                .andExpect(status().isOk());
    }

    private org.springframework.test.web.servlet.RequestBuilder checkout(UUID address) {
        return post("/orders")
                .header("Authorization", bearer)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"addressId\":\"" + address + "\"}");
    }
}
