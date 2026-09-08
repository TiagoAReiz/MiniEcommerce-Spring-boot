package reiz.miniecommerce.modules.shipments;

import reiz.miniecommerce.modules.address.core.entities.Address;
import reiz.miniecommerce.modules.address.core.interfaces.repositories.AddressRepository;
import reiz.miniecommerce.modules.auth.core.entities.AuthenticatedPrincipal;
import reiz.miniecommerce.modules.auth.core.interfaces.repositories.AccessTokenIssuer;
import reiz.miniecommerce.modules.cart.core.interfaces.repositories.CartRepository;
import reiz.miniecommerce.modules.orders.core.entities.Order;
import reiz.miniecommerce.modules.orders.core.interfaces.repositories.OrderRepository;
import reiz.miniecommerce.modules.owners.core.entities.Owner;
import reiz.miniecommerce.modules.owners.core.interfaces.repositories.OwnerRepository;
import reiz.miniecommerce.modules.payments.core.entities.GatewayPayment;
import reiz.miniecommerce.modules.payments.core.entities.PaymentIntent;
import reiz.miniecommerce.modules.payments.core.interfaces.repositories.PaymentGateway;
import reiz.miniecommerce.modules.payments.core.interfaces.repositories.PaymentRepository;
import reiz.miniecommerce.modules.products.core.entities.Product;
import reiz.miniecommerce.modules.products.core.interfaces.repositories.ProductRepository;
import reiz.miniecommerce.modules.users.core.entities.User;
import reiz.miniecommerce.modules.users.core.interfaces.repositories.UserRepository;
import reiz.miniecommerce.testsupport.Cpfs;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.AfterEach;
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

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Freight as the customer experiences it: quoted at checkout, frozen onto the order, and
 * charged by the payment gateway.
 *
 * <p>Covered end to end here: the shop with no origin, which refuses the sale outright, and
 * the shop whose postal-code lookup failed, which sells at the contingency rate. The measured
 * outcome is deliberately not asserted against a live provider — a test whose expected value
 * comes from a free public API is a test that fails on the provider's bad afternoon rather
 * than on a defect. The arithmetic behind it is pinned in {@link CoordinatesTest} instead,
 * without the network.
 *
 * <p>The CEP base URL points at a closed port so the real adapter runs, fails the way a
 * provider outage fails, and returns an empty answer. Nothing is stubbed on the way: the
 * contingency path is exercised through the same {@code RestClient} that production uses.
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        // an unreachable provider, not a fake one -- the adapter under test is the real one
        "app.shipping.cep-base-url=http://127.0.0.1:1",
        // pinned so the assertions do not depend on whatever SHIPPING_FALLBACK_COST holds
        "app.shipping.fallback-cost=17.50",
        "app.mercado-pago.access-token=test-token"
})
class ShippingQuoteCheckoutTest {

    private static final BigDecimal FALLBACK_COST = new BigDecimal("17.50");
    private static final BigDecimal UNIT_PRICE = new BigDecimal("49.90");
    private static final BigDecimal ITEMS_TOTAL = new BigDecimal("99.80");

    /**
     * Mercado Pago is replaced at the port, which is where the architecture already draws
     * the line. What is being checked is the amount handed across it, not what the provider
     * does with it.
     */
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
    @Autowired private CartRepository cartRepository;
    @Autowired private OrderRepository orderRepository;
    @Autowired private OwnerRepository ownerRepository;
    @Autowired private PaymentRepository paymentRepository;

    private UUID userId;
    private String bearer;
    private UUID addressId;
    private UUID productId;

    /**
     * There is one storefront row and it outlives the run, so these tests borrow it and have
     * to give it back. Leaving an origin behind would turn on freight for every later test
     * that assumes a shop which does not charge it.
     */
    private String originalOrigin;

    @BeforeEach
    void setUp() {
        User user = userRepository.save(User.fromGoogleProfile(
                "sub-" + UUID.randomUUID(), "Tiago", UUID.randomUUID() + "@exemplo.com", null));
        user.setCpf(Cpfs.random());
        user.setPhone("+5511999999999");
        user = userRepository.save(user);
        userId = user.getId();

        bearer = bearerFor(userId, AuthenticatedPrincipal.Role.USER);

        addressId = addressRepository.save(Address.builder()
                .userId(userId).zipCode(randomCep()).street("Avenida Paulista")
                .streetNumber("1578").neighborhood("Bela Vista").city("São Paulo")
                .state("SP").country("BR").primary(false)
                .build()).getId();

        productId = productRepository.save(Product.builder()
                .name("Caneca " + UUID.randomUUID())
                .price(UNIT_PRICE).stock(10).active(true)
                .build()).getId();

        cartRepository.deleteByUserId(userId);
        originalOrigin = store().getOriginZipCode();
    }

    @AfterEach
    void restoreTheStoreOrigin() {
        setStoreOrigin(originalOrigin);
    }

    /**
     * The state every fresh install is in, and the reason freight is mandatory rather than
     * opt-in: with no origin there is nothing to measure from, so the shop refuses the sale
     * instead of delivering for free.
     *
     * <p>A regression here would not look like a bug. Every order would simply come out
     * missing its freight, each one perfectly plausible on its own, and the only thing that
     * would ever notice is the accounting.
     */
    @Test
    void aStoreWithNoOriginRefusesTheCheckout() throws Exception {
        setStoreOrigin(null);
        addToCart(2);

        mockMvc.perform(checkout())
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SHIPPING_ORIGIN_NOT_CONFIGURED"));

        // The quote throws before anything is written, so the customer keeps their cart and
        // can go through with it the moment the operator configures the shop.
        assertThat(cartRepository.findByUserId(userId))
                .as("a refused checkout must not cost the customer their cart")
                .isPresent();
    }

    /**
     * An origin is configured but the provider cannot be reached. The sale must go through
     * at the contingency rate rather than fail — a free public CEP service being down is not
     * a reason to refuse a customer's money.
     *
     * <p>The null distance is asserted as hard as the cost is. It is the only record that
     * this charge was a flat rate and not a measurement, which is what makes the amount
     * explainable to a customer who disputes it and what makes "how often is the fallback
     * firing" a question the database can answer.
     */
    @Test
    void anUnreachableCepProviderFallsBackToAFlatRateWithNoDistance() throws Exception {
        setStoreOrigin(randomCep());
        addToCart(2);

        String body = mockMvc.perform(checkout())
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.itemsTotal").value(99.80))
                .andExpect(jsonPath("$.shippingCost").value(17.50))
                .andExpect(jsonPath("$.shippingDistanceKm").isEmpty())
                .andExpect(jsonPath("$.total").value(117.30))
                .andReturn().getResponse().getContentAsString();

        Order order = orderRepository.findById(orderIdOf(body)).orElseThrow();
        assertThat(order.getShippingCost()).isEqualByComparingTo(FALLBACK_COST);
        assertThat(order.getShippingDistanceKm())
                .as("a flat rate is not a measurement, and the null is how you can tell")
                .isNull();
    }

    /**
     * Freight is frozen at checkout, so moving the origin afterwards must not reprice an
     * order the customer already agreed to.
     */
    @Test
    void anOrderKeepsTheFreightItWasQuotedEvenAfterTheOriginMoves() throws Exception {
        setStoreOrigin(randomCep());
        addToCart(1);

        UUID orderId = orderIdOf(mockMvc.perform(checkout())
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString());

        setStoreOrigin(null);

        assertThat(orderRepository.findById(orderId).orElseThrow().getShippingCost())
                .isEqualByComparingTo(FALLBACK_COST);
    }

    /**
     * The amount the gateway is told to collect has to be the amount the customer was shown.
     * The two are computed in different classes, so nothing but a test keeps them equal — and
     * a drift here means either the shop eats the freight or the customer is charged for a
     * delivery they never saw quoted.
     */
    @Test
    void theChargeOpenedAtTheGatewayIsTheTotalIncludingFreight() throws Exception {
        setStoreOrigin(randomCep());
        addToCart(2);

        UUID orderId = orderIdOf(mockMvc.perform(checkout())
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.total").value(117.30))
                .andReturn().getResponse().getContentAsString());

        mockMvc.perform(post("/orders/" + orderId + "/payments").header("Authorization", bearer))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.amount").value(117.30));

        UUID paymentId = orderRepository.findById(orderId).orElseThrow().getPaymentId();
        assertThat(paymentRepository.findById(paymentId).orElseThrow().getAmount())
                .as("the payments row is what reconciliation compares against later")
                .isEqualByComparingTo(ITEMS_TOTAL.add(FALLBACK_COST));
    }

    // ---------- helpers ----------

    /**
     * A CEP no earlier test can have cached. The gateway caches successful lookups in Redis
     * for thirty days and Redis outlives the run, so a shared literal could answer with real
     * coordinates and quietly turn this into a measured quote instead of a fallback.
     */
    private String randomCep() {
        return String.format("%08d", ThreadLocalRandom.current().nextInt(10_000_000, 99_999_999));
    }

    private Owner store() {
        return ownerRepository.findStore().orElseThrow(
                () -> new IllegalStateException("No owner row: the V2 seed did not run"));
    }

    private void setStoreOrigin(String zipCode) {
        Owner store = store();
        store.setOriginZipCode(zipCode);
        ownerRepository.save(store);
    }

    private UUID orderIdOf(String responseBody) {
        return UUID.fromString(JsonPath.read(responseBody, "$.id"));
    }

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

    private org.springframework.test.web.servlet.RequestBuilder checkout() {
        return post("/orders")
                .header("Authorization", bearer)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"addressId\":\"" + addressId + "\"}");
    }
}
