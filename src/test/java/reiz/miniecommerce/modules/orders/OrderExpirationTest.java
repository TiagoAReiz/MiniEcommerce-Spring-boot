package reiz.miniecommerce.modules.orders;

import reiz.miniecommerce.modules.address.core.entities.Address;
import reiz.miniecommerce.modules.address.core.interfaces.repositories.AddressRepository;
import reiz.miniecommerce.modules.auth.core.entities.AuthenticatedPrincipal;
import reiz.miniecommerce.modules.auth.core.interfaces.repositories.AccessTokenIssuer;
import reiz.miniecommerce.modules.cart.core.interfaces.repositories.CartRepository;
import reiz.miniecommerce.modules.orders.application.services.OrderExpirationJob;
import reiz.miniecommerce.modules.orders.core.entities.Order;
import reiz.miniecommerce.modules.orders.core.entities.OrderStatus;
import reiz.miniecommerce.modules.orders.core.interfaces.repositories.OrderRepository;
import reiz.miniecommerce.modules.payments.core.entities.GatewayPayment;
import reiz.miniecommerce.modules.payments.core.entities.PaymentIntent;
import reiz.miniecommerce.modules.payments.core.interfaces.repositories.PaymentGateway;
import reiz.miniecommerce.modules.products.core.entities.Product;
import reiz.miniecommerce.modules.products.core.interfaces.repositories.ProductRepository;
import reiz.miniecommerce.modules.users.core.entities.User;
import reiz.miniecommerce.modules.users.core.interfaces.repositories.UserRepository;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.RequestBuilder;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Checkout takes the stock before any money exists, so an order nobody pays for holds a shelf
 * empty. These tests pin down the deadline that releases it and the trace it leaves behind.
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        // the sweep is driven by hand here; the scheduled run must not race the assertions
        "app.orders.expiration-interval=1h",
        "app.orders.reservation-window=30m",
        "app.orders.payment-window=24h",
        "app.mercado-pago.access-token=test-token"
})
class OrderExpirationTest {

    @TestConfiguration
    static class StubGateway {
        @Bean
        @Primary
        PaymentGateway paymentGateway() {
            return new PaymentGateway() {
                @Override
                public PaymentIntent openCharge(UUID paymentId, String description,
                                                BigDecimal amount, String payerEmail) {
                    return PaymentIntent.builder()
                            .gatewayReference("pref-" + paymentId)
                            .checkoutUrl("https://mercadopago.com/checkout/pref-" + paymentId)
                            .build();
                }

                @Override
                public Optional<GatewayPayment> findPayment(String gatewayPaymentId) {
                    return Optional.empty();
                }

                @Override
                public Optional<GatewayPayment> findByExternalReference(UUID paymentId) {
                    return Optional.empty();
                }
            };
        }
    }

    @Autowired private MockMvc mockMvc;
    @Autowired private AccessTokenIssuer tokenIssuer;
    @Autowired private UserRepository userRepository;
    @Autowired private AddressRepository addressRepository;
    @Autowired private ProductRepository productRepository;
    @Autowired private OrderRepository orderRepository;
    @Autowired private CartRepository cartRepository;
    @Autowired private OrderExpirationJob expirationJob;

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
        userId = userRepository.save(user).getId();

        bearer = bearerFor(userId, AuthenticatedPrincipal.Role.USER);
        ownerBearer = bearerFor(UUID.randomUUID(), AuthenticatedPrincipal.Role.OWNER);

        addressId = addressRepository.save(Address.builder()
                .userId(userId).zipCode("01310100").street("Avenida Paulista")
                .streetNumber("1578").neighborhood("Bela Vista").city("Sao Paulo")
                .state("SP").country("BR").primary(false)
                .build()).getId();

        productId = productRepository.save(Product.builder()
                .name("Caneca " + UUID.randomUUID())
                .price(new BigDecimal("49.90")).stock(10).active(true)
                .build()).getId();

        cartRepository.deleteByUserId(userId);
    }

    @Test
    void checkoutStartsTheReservationClock() throws Exception {
        addToCart(2);
        OffsetDateTime before = OffsetDateTime.now();

        UUID orderId = checkedOutOrder();

        OffsetDateTime expiresAt = order(orderId).getExpiresAt();
        assertThat(expiresAt)
                .isAfter(before.plusMinutes(29))
                .isBefore(before.plusMinutes(31));
    }

    @Test
    void anAbandonedOrderIsCancelledAndGivesTheStockBack() throws Exception {
        addToCart(3);
        UUID orderId = checkedOutOrder();
        assertThat(stock()).isEqualTo(7);

        // the sweep takes the instant explicitly, so the reservation can age without waiting
        assertThat(expirationJob.sweep(OffsetDateTime.now().plusHours(1))).isPositive();

        assertThat(order(orderId).getStatus()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(stock()).isEqualTo(10);
    }

    @Test
    void theCancelledOrderKeepsItsDeadlineAsTheRecordOfTheLostSale() throws Exception {
        addToCart(1);
        UUID orderId = checkedOutOrder();
        OffsetDateTime deadline = order(orderId).getExpiresAt();

        expirationJob.sweep(OffsetDateTime.now().plusHours(1));

        Order expired = order(orderId);
        assertThat(expired.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        // this is the whole point of keeping the column: a cancelled order that still carries
        // a deadline was walked away from, not cancelled by hand
        assertThat(expired.getExpiresAt()).isEqualTo(deadline);

        mockMvc.perform(get("/orders/" + orderId).header("Authorization", bearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.expiresAt").isNotEmpty())
                .andExpect(jsonPath("$.items[0].quantity").value(1));
    }

    @Test
    void aReservationThatHasNotRunOutIsLeftAlone() throws Exception {
        addToCart(2);
        UUID orderId = checkedOutOrder();

        expirationJob.sweep(OffsetDateTime.now());

        assertThat(order(orderId).getStatus()).isEqualTo(OrderStatus.PENDING);
        assertThat(stock()).isEqualTo(8);
    }

    @Test
    void payingClearsTheDeadlineSoTheSweepCanNeverTouchTheOrder() throws Exception {
        addToCart(1);
        UUID orderId = checkedOutOrder();

        mockMvc.perform(patch("/orders/" + orderId + "/status")
                        .header("Authorization", ownerBearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"PAID\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.expiresAt").doesNotExist());

        assertThat(order(orderId).getExpiresAt()).isNull();

        expirationJob.sweep(OffsetDateTime.now().plusDays(365));
        assertThat(order(orderId).getStatus()).isEqualTo(OrderStatus.PAID);
        assertThat(stock()).isEqualTo(9);
    }

    @Test
    void openingAChargeBuysTheCustomerTheLongerWindow() throws Exception {
        addToCart(1);
        UUID orderId = checkedOutOrder();

        mockMvc.perform(post("/orders/" + orderId + "/payments").header("Authorization", bearer))
                .andExpect(status().isCreated());

        // the customer is at the payment screen: cancelling them out an hour later could
        // restock an order that is about to be approved
        assertThat(order(orderId).getExpiresAt()).isAfter(OffsetDateTime.now().plusHours(23));

        expirationJob.sweep(OffsetDateTime.now().plusHours(1));
        assertThat(order(orderId).getStatus()).isEqualTo(OrderStatus.PENDING);
        assertThat(stock()).isEqualTo(9);
    }

    // ---------- helpers ----------

    private String bearerFor(UUID id, AuthenticatedPrincipal.Role role) {
        return "Bearer " + tokenIssuer.issue(AuthenticatedPrincipal.builder()
                .id(id).email(id + "@exemplo.com").role(role).build()).getToken();
    }

    private void addToCart(int quantity) throws Exception {
        mockMvc.perform(post("/cart/items")
                        .header("Authorization", bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productId\":\"" + productId + "\",\"quantity\":" + quantity + "}"))
                .andExpect(status().isOk());
    }

    private RequestBuilder checkout() {
        return post("/orders")
                .header("Authorization", bearer)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"addressId\":\"" + addressId + "\"}");
    }

    private UUID checkedOutOrder() throws Exception {
        String body = mockMvc.perform(checkout())
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return UUID.fromString(JsonPath.read(body, "$.id"));
    }

    private Order order(UUID id) {
        return orderRepository.findById(id).orElseThrow();
    }

    private int stock() {
        return productRepository.findById(productId).orElseThrow().getStock();
    }
}
