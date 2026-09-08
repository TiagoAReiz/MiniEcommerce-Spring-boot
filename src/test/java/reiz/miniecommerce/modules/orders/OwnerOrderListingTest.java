package reiz.miniecommerce.modules.orders;

import reiz.miniecommerce.modules.address.core.entities.Address;
import reiz.miniecommerce.modules.address.core.interfaces.repositories.AddressRepository;
import reiz.miniecommerce.modules.auth.core.entities.AuthenticatedPrincipal;
import reiz.miniecommerce.modules.auth.core.interfaces.repositories.AccessTokenIssuer;
import reiz.miniecommerce.modules.cart.core.interfaces.repositories.CartRepository;
import reiz.miniecommerce.modules.owners.core.interfaces.repositories.OwnerRepository;
import reiz.miniecommerce.modules.products.core.entities.Product;
import reiz.miniecommerce.modules.products.core.interfaces.repositories.ProductRepository;
import reiz.miniecommerce.modules.users.core.entities.User;
import reiz.miniecommerce.modules.users.core.interfaces.repositories.UserRepository;
import reiz.miniecommerce.testsupport.Cpfs;
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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * {@code GET /orders/all}: a loja inteira, só para o dono.
 *
 * <p>Existe porque {@code GET /orders} recorta por quem chama, e o {@code sub} de um token de
 * dono é o id do dono — sem esta rota o painel enxergaria os pedidos que o próprio dono fez
 * como cliente, e nada mais.
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "app.shipping.cep-base-url=http://127.0.0.1:1",
        "app.shipping.fallback-cost=0.00",
})
class OwnerOrderListingTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private AccessTokenIssuer tokenIssuer;
    @Autowired private UserRepository userRepository;
    @Autowired private AddressRepository addressRepository;
    @Autowired private ProductRepository productRepository;
    @Autowired private CartRepository cartRepository;
    @Autowired private OwnerRepository ownerRepository;

    private String clienteBearer;
    private String ownerBearer;
    private UUID addressId;
    private UUID productId;
    private UUID clienteId;

    @BeforeEach
    void setUp() {
        Storefront.sellsWithFreight(ownerRepository);

        User cliente = userRepository.save(User.fromGoogleProfile(
                "sub-" + UUID.randomUUID(), "Camila", UUID.randomUUID() + "@exemplo.com", null));
        cliente.setCpf(Cpfs.random());
        cliente.setPhone("+5511999999999");
        cliente = userRepository.save(cliente);
        clienteId = cliente.getId();

        clienteBearer = bearerFor(clienteId, AuthenticatedPrincipal.Role.USER);
        ownerBearer = bearerFor(UUID.randomUUID(), AuthenticatedPrincipal.Role.OWNER);

        addressId = addressRepository.save(Address.builder()
                .userId(clienteId).zipCode("01310100").street("Avenida Paulista")
                .streetNumber("1578").neighborhood("Bela Vista").city("São Paulo")
                .state("SP").country("BR").primary(false)
                .build()).getId();

        productId = productRepository.save(Product.builder()
                .name("Monitor " + UUID.randomUUID())
                .price(new BigDecimal("2449.00")).stock(50).active(true)
                .build()).getId();

        cartRepository.deleteByUserId(clienteId);
    }

    /**
     * O que a rota existe para fazer, e o que {@code GET /orders} não faz: o pedido é de outra
     * pessoa, e o dono precisa vê-lo para despachar.
     */
    @Test
    void theOwnerSeesAnOrderPlacedBySomebodyElse() throws Exception {
        UUID orderId = fecharPedido();

        mockMvc.perform(get("/orders/all").header("Authorization", ownerBearer).param("size", "100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[?(@.id == '" + orderId + "')]").exists());
    }

    /** O mesmo pedido não aparece em {@code GET /orders} do dono — lá o recorte é por dono. */
    @Test
    void theSameOrderIsNotInTheOwnersOwnListing() throws Exception {
        UUID orderId = fecharPedido();

        mockMvc.perform(get("/orders").header("Authorization", ownerBearer).param("size", "100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[?(@.id == '" + orderId + "')]").doesNotExist());
    }

    /**
     * A rota é fechada, e a checagem vive no {@code @PreAuthorize} da camada HTTP — o service
     * não filtra por papel de propósito, então esta é a única coisa que separa a loja inteira
     * de qualquer cliente logado.
     */
    @Test
    void aPlainUserIsRefused() throws Exception {
        mockMvc.perform(get("/orders/all").header("Authorization", clienteBearer))
                .andExpect(status().isForbidden());
    }

    @Test
    void anonymousIsRefused() throws Exception {
        mockMvc.perform(get("/orders/all"))
                .andExpect(status().isUnauthorized());
    }

    /** Mesmo formato de página da busca de produtos, incluindo o teto de 100 em `size`. */
    @Test
    void itPagesLikeTheProductSearch() throws Exception {
        fecharPedido();

        mockMvc.perform(get("/orders/all").header("Authorization", ownerBearer)
                        .param("page", "0").param("size", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(1))
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.totalElements").isNumber());

        mockMvc.perform(get("/orders/all").header("Authorization", ownerBearer).param("size", "500"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.size").value(100));
    }

    /**
     * Mais recentes primeiro. O dono abre esta lista para achar o que precisa de ação, e o que
     * precisa de ação é o que acabou de entrar.
     */
    @Test
    void theNewestOrderComesFirst() throws Exception {
        fecharPedido();
        UUID ultimo = fecharPedido();

        mockMvc.perform(get("/orders/all").header("Authorization", ownerBearer).param("size", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(ultimo.toString()));
    }

    // ---------- helpers ----------

    private UUID fecharPedido() throws Exception {
        mockMvc.perform(post("/cart/items")
                        .header("Authorization", clienteBearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productId\":\"" + productId + "\",\"quantity\":1}"))
                .andExpect(status().isOk());

        String body = mockMvc.perform(post("/orders")
                        .header("Authorization", clienteBearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"addressId\":\"" + addressId + "\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        return UUID.fromString(JsonPath.read(body, "$.id"));
    }

    private String bearerFor(UUID id, AuthenticatedPrincipal.Role role) {
        return "Bearer " + tokenIssuer.issue(AuthenticatedPrincipal.builder()
                .id(id).email(id + "@exemplo.com").role(role).build()).getToken();
    }
}
