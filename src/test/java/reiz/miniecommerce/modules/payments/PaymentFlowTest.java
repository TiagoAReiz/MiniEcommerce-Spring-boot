package reiz.miniecommerce.modules.payments;

import reiz.miniecommerce.modules.address.core.entities.Address;
import reiz.miniecommerce.modules.address.core.interfaces.repositories.AddressRepository;
import reiz.miniecommerce.modules.auth.core.entities.AuthenticatedPrincipal;
import reiz.miniecommerce.modules.auth.core.interfaces.repositories.AccessTokenIssuer;
import reiz.miniecommerce.modules.orders.core.entities.OrderStatus;
import reiz.miniecommerce.modules.orders.core.interfaces.repositories.OrderRepository;
import reiz.miniecommerce.modules.payments.core.entities.GatewayPayment;
import reiz.miniecommerce.modules.payments.core.entities.PaymentIntent;
import reiz.miniecommerce.modules.payments.core.interfaces.repositories.PaymentGateway;
import reiz.miniecommerce.modules.payments.core.interfaces.repositories.PaymentRepository;
import reiz.miniecommerce.modules.products.core.entities.Product;
import reiz.miniecommerce.modules.products.core.interfaces.repositories.ProductRepository;
import reiz.miniecommerce.modules.users.core.entities.User;
import reiz.miniecommerce.modules.users.core.interfaces.repositories.UserRepository;
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

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Exercises the payment flow with a stub gateway. The port exists precisely so this can be
 * tested without reaching Mercado Pago; the signature and idempotency logic, which is the
 * part worth guarding, is the real implementation.
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "app.mercado-pago.webhook-secret=segredo-de-teste-do-webhook",
        "app.mercado-pago.access-token=test-token"
})
class PaymentFlowTest {

    private static final String SECRET = "segredo-de-teste-do-webhook";

    @TestConfiguration
    static class StubGateway {

        static final Map<String, GatewayPayment> PAYMENTS = new ConcurrentHashMap<>();
        static final List<UUID> OPENED = java.util.Collections.synchronizedList(new java.util.ArrayList<>());

        @Bean
        @Primary
        PaymentGateway paymentGateway() {
            return new PaymentGateway() {
                @Override
                public PaymentIntent openCharge(UUID paymentId, String description,
                                                BigDecimal amount, String payerEmail) {
                    OPENED.add(paymentId);
                    return PaymentIntent.builder()
                            .gatewayReference("pref-" + paymentId)
                            .checkoutUrl("https://mercadopago.com/checkout/pref-" + paymentId)
                            .build();
                }

                @Override
                public Optional<GatewayPayment> findPayment(String gatewayPaymentId) {
                    return Optional.ofNullable(PAYMENTS.get(gatewayPaymentId));
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
    @Autowired private PaymentRepository paymentRepository;

    private String bearer;
    private UUID orderId;

    @BeforeEach
    void placeAnOrder() throws Exception {
        StubGateway.PAYMENTS.clear();
        StubGateway.OPENED.clear();

        User user = userRepository.save(User.fromGoogleProfile(
                "sub-" + UUID.randomUUID(), "Tiago", UUID.randomUUID() + "@exemplo.com", null));
        user.setCpf(String.valueOf(System.nanoTime()).substring(0, 11));
        user.setPhone("+5511999999999");
        user = userRepository.save(user);

        bearer = "Bearer " + tokenIssuer.issue(AuthenticatedPrincipal.builder()
                .id(user.getId()).email(user.getEmail())
                .role(AuthenticatedPrincipal.Role.USER).build()).getToken();

        UUID addressId = addressRepository.save(Address.builder()
                .userId(user.getId()).zipCode("01310100").street("Avenida Paulista")
                .neighborhood("Bela Vista").city("São Paulo").state("SP").country("BR")
                .primary(false).build()).getId();

        UUID productId = productRepository.save(Product.builder()
                .name("Caneca " + UUID.randomUUID())
                .price(new BigDecimal("49.90")).stock(10).active(true).build()).getId();

        mockMvc.perform(post("/cart/items").header("Authorization", bearer)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"productId\":\"" + productId + "\",\"quantity\":2}"));

        String created = mockMvc.perform(post("/orders").header("Authorization", bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"addressId\":\"" + addressId + "\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        orderId = UUID.fromString(com.jayway.jsonpath.JsonPath.read(created, "$.id"));
    }

    @Test
    void openingAChargeReturnsTheCheckoutUrlAndTheOrderTotal() throws Exception {
        mockMvc.perform(post("/orders/" + orderId + "/payments").header("Authorization", bearer))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.amount").value(99.80))
                .andExpect(jsonPath("$.checkoutUrl").value(org.hamcrest.Matchers.startsWith(
                        "https://mercadopago.com/checkout/")));

        assertThat(StubGateway.OPENED).hasSize(1);
    }

    @Test
    void aChargeCannotBeOpenedTwiceForTheSameOrder() throws Exception {
        mockMvc.perform(post("/orders/" + orderId + "/payments").header("Authorization", bearer))
                .andExpect(status().isCreated());

        settle();

        mockMvc.perform(post("/orders/" + orderId + "/payments").header("Authorization", bearer))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ORDER_ALREADY_PAID"));
    }

    @Test
    void anApprovedNotificationSettlesThePaymentAndAdvancesTheOrder() throws Exception {
        UUID paymentId = openCharge();
        String gatewayId = registerApproved(paymentId);

        webhook(gatewayId, signatureFor(gatewayId, "req-1"), "req-1")
                .andExpect(status().isOk());

        assertThat(awaitPaid(paymentId)).as("pagamento liquidado em background").isTrue();
        assertThat(paymentRepository.findById(paymentId).orElseThrow().getMercadoPagoId())
                .isEqualTo(gatewayId);
        assertThat(orderRepository.findById(orderId).orElseThrow().getStatus())
                .isEqualTo(OrderStatus.PAID);
    }

    @Test
    void aForgedSignatureIsRejectedAndNothingIsSettled() throws Exception {
        UUID paymentId = openCharge();
        String gatewayId = registerApproved(paymentId);

        webhook(gatewayId, "ts=123,v1=deadbeef", "req-1")
                .andExpect(status().isUnauthorized());

        assertThat(paymentRepository.findById(paymentId).orElseThrow().isPaid()).isFalse();
        assertThat(orderRepository.findById(orderId).orElseThrow().getStatus())
                .isEqualTo(OrderStatus.PENDING);
    }

    @Test
    void aMissingSignatureIsRejected() throws Exception {
        UUID paymentId = openCharge();
        String gatewayId = registerApproved(paymentId);

        mockMvc.perform(post("/webhooks/mercado-pago")
                        .param("data.id", gatewayId).param("type", "payment")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"payment\",\"data\":{\"id\":\"" + gatewayId + "\"}}"))
                .andExpect(status().isUnauthorized());

        assertThat(paymentRepository.findById(paymentId).orElseThrow().isPaid()).isFalse();
    }

    @Test
    void aRetriedNotificationIsAcceptedButNotProcessedTwice() throws Exception {
        UUID paymentId = openCharge();
        String gatewayId = registerApproved(paymentId);

        webhook(gatewayId, signatureFor(gatewayId, "req-1"), "req-1").andExpect(status().isOk());

        assertThat(awaitPaid(paymentId)).isTrue();

        // Mercado Pago redelivers; the second one must still answer 200 or it retries forever
        webhook(gatewayId, signatureFor(gatewayId, "req-1"), "req-1").andExpect(status().isOk());

        assertThat(paymentRepository.findById(paymentId).orElseThrow().isPaid()).isTrue();
        assertThat(orderRepository.findById(orderId).orElseThrow().getStatus())
                .isEqualTo(OrderStatus.PAID);
    }

    @Test
    void aPendingPaymentDoesNotSettleTheOrder() throws Exception {
        UUID paymentId = openCharge();
        String gatewayId = "mp-" + UUID.randomUUID();
        StubGateway.PAYMENTS.put(gatewayId, GatewayPayment.builder()
                .gatewayId(gatewayId).externalReference(paymentId.toString())
                .status("pending").build());

        webhook(gatewayId, signatureFor(gatewayId, "req-1"), "req-1").andExpect(status().isOk());

        assertThat(paymentRepository.findById(paymentId).orElseThrow().isPaid()).isFalse();
        assertThat(orderRepository.findById(orderId).orElseThrow().getStatus())
                .isEqualTo(OrderStatus.PENDING);
    }

    @Test
    void anUnknownNotificationIsStillAcceptedQuietly() throws Exception {
        String gatewayId = "mp-desconhecido-" + UUID.randomUUID();

        webhook(gatewayId, signatureFor(gatewayId, "req-1"), "req-1")
                .andExpect(status().isOk());
    }

    // ---------- helpers ----------

    /**
     * Settlement runs off the request thread now, so the effect lands shortly after the 200.
     * Polling is the honest way to assert it without pretending the work is synchronous.
     */
    private boolean awaitPaid(UUID paymentId) {
        long deadline = System.nanoTime() + java.time.Duration.ofSeconds(10).toNanos();
        while (System.nanoTime() < deadline) {
            if (paymentRepository.findById(paymentId).map(p -> p.isPaid()).orElse(false)) {
                return true;
            }
        }
        return false;
    }

    private UUID openCharge() throws Exception {
        mockMvc.perform(post("/orders/" + orderId + "/payments").header("Authorization", bearer))
                .andExpect(status().isCreated());
        return orderRepository.findById(orderId).orElseThrow().getPaymentId();
    }

    private String registerApproved(UUID paymentId) {
        String gatewayId = "mp-" + UUID.randomUUID();
        StubGateway.PAYMENTS.put(gatewayId, GatewayPayment.builder()
                .gatewayId(gatewayId)
                .externalReference(paymentId.toString())
                .status("approved")
                .build());
        return gatewayId;
    }

    private void settle() throws Exception {
        UUID paymentId = orderRepository.findById(orderId).orElseThrow().getPaymentId();
        String gatewayId = registerApproved(paymentId);
        webhook(gatewayId, signatureFor(gatewayId, "req-settle"), "req-settle")
                .andExpect(status().isOk());
        assertThat(awaitPaid(paymentId)).as("liquidacao concluida antes de seguir").isTrue();
    }

    private org.springframework.test.web.servlet.ResultActions webhook(
            String dataId, String signature, String requestId) throws Exception {
        return mockMvc.perform(post("/webhooks/mercado-pago")
                .header("x-signature", signature)
                .header("x-request-id", requestId)
                .param("data.id", dataId)
                .param("type", "payment")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"type\":\"payment\",\"data\":{\"id\":\"" + dataId + "\"}}"));
    }

    /** Builds the header the way Mercado Pago documents it. */
    private String signatureFor(String dataId, String requestId) throws Exception {
        long ts = System.currentTimeMillis();
        String manifest = "id:%s;request-id:%s;ts:%d;".formatted(dataId.toLowerCase(), requestId, ts);

        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        String v1 = HexFormat.of().formatHex(mac.doFinal(manifest.getBytes(StandardCharsets.UTF_8)));

        return "ts=%d,v1=%s".formatted(ts, v1);
    }
}
