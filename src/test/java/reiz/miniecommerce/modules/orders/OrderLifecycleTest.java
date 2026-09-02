package reiz.miniecommerce.modules.orders;

import reiz.miniecommerce.modules.address.core.entities.Address;
import reiz.miniecommerce.modules.address.core.interfaces.repositories.AddressRepository;
import reiz.miniecommerce.modules.auth.core.entities.AuthenticatedPrincipal;
import reiz.miniecommerce.modules.auth.core.interfaces.repositories.AccessTokenIssuer;
import reiz.miniecommerce.modules.products.core.entities.Product;
import reiz.miniecommerce.modules.products.core.interfaces.repositories.ProductRepository;
import reiz.miniecommerce.modules.users.core.entities.User;
import reiz.miniecommerce.modules.users.core.interfaces.repositories.UserRepository;
import com.jayway.jsonpath.JsonPath;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Walks an order from checkout to a published review, and checks the gates along the way.
 */
@SpringBootTest
@AutoConfigureMockMvc
class OrderLifecycleTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private AccessTokenIssuer tokenIssuer;
    @Autowired private UserRepository userRepository;
    @Autowired private AddressRepository addressRepository;
    @Autowired private ProductRepository productRepository;

    private String bearer;
    private String ownerBearer;
    private UUID productId;
    private String orderId;

    @BeforeEach
    void placeAnOrder() throws Exception {
        User user = customer();
        bearer = bearerFor(user.getId(), AuthenticatedPrincipal.Role.USER);
        ownerBearer = bearerFor(UUID.randomUUID(), AuthenticatedPrincipal.Role.OWNER);

        UUID addressId = addressFor(user.getId());

        productId = productRepository.save(Product.builder()
                .name("Caneca " + UUID.randomUUID())
                .price(new BigDecimal("49.90")).stock(10).active(true).build()).getId();

        mockMvc.perform(post("/cart/items").header("Authorization", bearer)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"productId\":\"" + productId + "\",\"quantity\":1}"));

        orderId = JsonPath.read(mockMvc.perform(post("/orders").header("Authorization", bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"addressId\":\"" + addressId + "\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString(), "$.id");
    }

    @Test
    void fromPaidToDeliveredToReviewed() throws Exception {
        advanceTo("PAID");

        String shipmentId = JsonPath.read(mockMvc.perform(post("/orders/" + orderId + "/shipment")
                        .header("Authorization", ownerBearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"estimatedDeliveryAt\":\"2026-09-10T12:00:00-03:00\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.shipped").value(false))
                .andReturn().getResponse().getContentAsString(), "$.id");

        // dispatching the parcel is what moves the order to SHIPPED
        mockMvc.perform(patch("/shipments/" + shipmentId)
                        .header("Authorization", ownerBearer)
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.shipped").value(true));

        mockMvc.perform(get("/orders/" + orderId).header("Authorization", bearer))
                .andExpect(jsonPath("$.status").value("SHIPPED"));

        advanceTo("DELIVERED");

        String orderItemId = JsonPath.read(
                mockMvc.perform(get("/users/me/pending-reviews").header("Authorization", bearer))
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.length()").value(1))
                        .andReturn().getResponse().getContentAsString(), "$[0].orderItemId");

        mockMvc.perform(post("/order-items/" + orderItemId + "/reviews")
                        .header("Authorization", bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"rating\":5,\"title\":\"Ótima\",\"comment\":\"Chegou rápido\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.rating").value(5));

        // reviews are public, and the summary covers every review of the product
        mockMvc.perform(get("/products/" + productId + "/reviews"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalReviews").value(1))
                .andExpect(jsonPath("$.averageRating").value(5.0))
                .andExpect(jsonPath("$.reviews.content[0].title").value("Ótima"));

        // and the reminder list is empty once it has been rated
        mockMvc.perform(get("/users/me/pending-reviews").header("Authorization", bearer))
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void nothingShipsBeforeItIsPaid() throws Exception {
        mockMvc.perform(post("/orders/" + orderId + "/shipment")
                        .header("Authorization", ownerBearer)
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ORDER_NOT_PAID"));
    }

    @Test
    void anOrderGetsOnlyOneShipment() throws Exception {
        advanceTo("PAID");
        createShipment();

        mockMvc.perform(post("/orders/" + orderId + "/shipment")
                        .header("Authorization", ownerBearer)
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SHIPMENT_ALREADY_EXISTS"));
    }

    @Test
    void customersCannotCreateShipments() throws Exception {
        advanceTo("PAID");

        mockMvc.perform(post("/orders/" + orderId + "/shipment")
                        .header("Authorization", bearer)
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void anUndeliveredItemCannotBeReviewed() throws Exception {
        advanceTo("PAID");
        String orderItemId = firstItemId();

        mockMvc.perform(post("/order-items/" + orderItemId + "/reviews")
                        .header("Authorization", bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"rating\":5}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ORDER_NOT_DELIVERED"));
    }

    @Test
    void anItemIsReviewedOnlyOnce() throws Exception {
        String orderItemId = deliveredItem();

        review(orderItemId, 5).andExpect(status().isCreated());
        review(orderItemId, 4)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ALREADY_REVIEWED"));
    }

    @Test
    void aRatingOutsideOneToFiveIsRejected() throws Exception {
        String orderItemId = deliveredItem();

        review(orderItemId, 6)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void someoneElsesItemLooksLikeItDoesNotExist() throws Exception {
        String orderItemId = deliveredItem();
        String strangerBearer = bearerFor(customer().getId(), AuthenticatedPrincipal.Role.USER);

        mockMvc.perform(post("/order-items/" + orderItemId + "/reviews")
                        .header("Authorization", strangerBearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"rating\":1}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void someoneElsesReviewIsForbiddenNotHidden() throws Exception {
        String orderItemId = deliveredItem();
        String reviewId = JsonPath.read(
                review(orderItemId, 5).andReturn().getResponse().getContentAsString(), "$.id");

        String strangerBearer = bearerFor(customer().getId(), AuthenticatedPrincipal.Role.USER);

        // the review is public, so hiding it buys nothing: 403, not 404
        mockMvc.perform(put("/reviews/" + reviewId)
                        .header("Authorization", strangerBearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"rating\":1,\"title\":\"sequestrada\"}"))
                .andExpect(status().isForbidden());

        mockMvc.perform(delete("/reviews/" + reviewId).header("Authorization", strangerBearer))
                .andExpect(status().isForbidden());
    }

    @Test
    void theAuthorCanEditAndRemoveTheirReview() throws Exception {
        String orderItemId = deliveredItem();
        String reviewId = JsonPath.read(
                review(orderItemId, 5).andReturn().getResponse().getContentAsString(), "$.id");

        mockMvc.perform(put("/reviews/" + reviewId)
                        .header("Authorization", bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"rating\":3,\"title\":\"Mudei de ideia\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rating").value(3));

        mockMvc.perform(delete("/reviews/" + reviewId).header("Authorization", bearer))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/products/" + productId + "/reviews"))
                .andExpect(jsonPath("$.totalReviews").value(0));
    }

    // ---------- helpers ----------

    private User customer() {
        User user = userRepository.save(User.fromGoogleProfile(
                "sub-" + UUID.randomUUID(), "Tiago", UUID.randomUUID() + "@exemplo.com", null));
        user.setCpf(String.valueOf(System.nanoTime()).substring(0, 11));
        user.setPhone("+5511999999999");
        return userRepository.save(user);
    }

    private UUID addressFor(UUID userId) {
        return addressRepository.save(Address.builder()
                .userId(userId).zipCode("01310100").street("Avenida Paulista")
                .neighborhood("Bela Vista").city("São Paulo").state("SP").country("BR")
                .primary(false).build()).getId();
    }

    private String bearerFor(UUID id, AuthenticatedPrincipal.Role role) {
        return "Bearer " + tokenIssuer.issue(AuthenticatedPrincipal.builder()
                .id(id).email(id + "@exemplo.com").role(role).build()).getToken();
    }

    private void advanceTo(String status) throws Exception {
        mockMvc.perform(patch("/orders/" + orderId + "/status")
                        .header("Authorization", ownerBearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"" + status + "\"}"))
                .andExpect(status().isOk());
    }

    private void createShipment() throws Exception {
        mockMvc.perform(post("/orders/" + orderId + "/shipment")
                        .header("Authorization", ownerBearer)
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isCreated());
    }

    private String firstItemId() throws Exception {
        return JsonPath.read(mockMvc.perform(get("/orders/" + orderId).header("Authorization", bearer))
                .andReturn().getResponse().getContentAsString(), "$.items[0].id");
    }

    /** Drives the order all the way to DELIVERED and hands back its only item. */
    private String deliveredItem() throws Exception {
        advanceTo("PAID");
        createShipment();
        advanceTo("SHIPPED");
        advanceTo("DELIVERED");
        return firstItemId();
    }

    private org.springframework.test.web.servlet.ResultActions review(String orderItemId, int rating)
            throws Exception {
        return mockMvc.perform(post("/order-items/" + orderItemId + "/reviews")
                .header("Authorization", bearer)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"rating\":" + rating + ",\"title\":\"Boa\"}"));
    }
}
