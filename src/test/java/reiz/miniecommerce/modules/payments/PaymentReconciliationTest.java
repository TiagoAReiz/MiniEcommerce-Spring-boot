package reiz.miniecommerce.modules.payments;

import reiz.miniecommerce.modules.address.core.entities.Address;
import reiz.miniecommerce.modules.address.core.interfaces.repositories.AddressRepository;
import reiz.miniecommerce.modules.auth.core.entities.AuthenticatedPrincipal;
import reiz.miniecommerce.modules.auth.core.interfaces.repositories.AccessTokenIssuer;
import reiz.miniecommerce.modules.orders.core.entities.OrderStatus;
import reiz.miniecommerce.modules.orders.core.interfaces.repositories.OrderRepository;
import reiz.miniecommerce.modules.payments.application.services.PaymentReconciliationJob;
import reiz.miniecommerce.modules.payments.core.entities.GatewayPayment;
import reiz.miniecommerce.modules.payments.core.entities.PaymentIntent;
import reiz.miniecommerce.modules.payments.core.exceptions.PaymentGatewayException;
import reiz.miniecommerce.modules.payments.core.interfaces.repositories.PaymentGateway;
import reiz.miniecommerce.modules.payments.core.interfaces.repositories.PaymentRepository;
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
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The safety net for payments the webhook never settled.
 *
 * <p>That gap is real: the webhook answers 200 before doing the work, so the gateway stops
 * retrying, and a background failure would leave a paid order sitting on PENDING with nothing
 * anywhere pointing at it.
 */
@SpringBootTest
@AutoConfigureMockMvc
class PaymentReconciliationTest {

    /** Approved payments, keyed by the external reference we sent when opening the charge. */
    @TestConfiguration
    static class GatewayWithApprovals {

        static final Map<UUID, GatewayPayment> APPROVED = new ConcurrentHashMap<>();
        static final java.util.Set<UUID> UNREACHABLE = ConcurrentHashMap.newKeySet();

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
                    if (UNREACHABLE.contains(paymentId)) {
                        throw new PaymentGatewayException("gateway fora do ar", null);
                    }
                    return Optional.ofNullable(APPROVED.get(paymentId));
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
    @Autowired private PaymentReconciliationJob reconciliationJob;

    private String bearer;
    private UUID orderId;

    @BeforeEach
    void placeAnOrderAndOpenACharge() throws Exception {
        GatewayWithApprovals.APPROVED.clear();
        GatewayWithApprovals.UNREACHABLE.clear();

        User user = userRepository.save(User.fromGoogleProfile(
                "sub-" + UUID.randomUUID(), "Tiago", UUID.randomUUID() + "@exemplo.com", null));
        user.setCpf(UUID.randomUUID().toString().replaceAll("\\D", "").substring(0, 11));
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
                .content("{\"productId\":\"" + productId + "\",\"quantity\":1}"));

        orderId = UUID.fromString(JsonPath.read(
                mockMvc.perform(post("/orders").header("Authorization", bearer)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"addressId\":\"" + addressId + "\"}"))
                        .andExpect(status().isCreated())
                        .andReturn().getResponse().getContentAsString(), "$.id"));

        mockMvc.perform(post("/orders/" + orderId + "/payments").header("Authorization", bearer))
                .andExpect(status().isCreated());
    }

    @Test
    void settlesAPaymentTheWebhookNeverDelivered() {
        UUID paymentId = paymentId();
        String gatewayId = approve(paymentId);

        // no webhook ever arrived: the charge is open and the order still pending
        assertThat(paymentRepository.findById(paymentId).orElseThrow().isPaid()).isFalse();
        assertThat(orderRepository.findById(orderId).orElseThrow().getStatus())
                .isEqualTo(OrderStatus.PENDING);

        sweep();

        assertThat(paymentRepository.findById(paymentId).orElseThrow().isPaid()).isTrue();
        assertThat(paymentRepository.findById(paymentId).orElseThrow().getMercadoPagoId())
                .isEqualTo(gatewayId);
        assertThat(orderRepository.findById(orderId).orElseThrow().getStatus())
                .isEqualTo(OrderStatus.PAID);
    }

    @Test
    void leavesAChargeAloneWhenTheGatewayHasNotApprovedIt() {
        UUID paymentId = paymentId();

        sweep();

        assertThat(paymentRepository.findById(paymentId).orElseThrow().isPaid()).isFalse();
        assertThat(orderRepository.findById(orderId).orElseThrow().getStatus())
                .isEqualTo(OrderStatus.PENDING);
    }

    @Test
    void doesNotSettleTheSameChargeTwice() {
        UUID paymentId = paymentId();
        String gatewayId = approve(paymentId);

        assertThat(sweep()).as("primeira passada liquida").isPositive();
        assertThat(paymentRepository.findById(paymentId).orElseThrow().isPaid()).isTrue();

        // already settled, so it is no longer a candidate at all
        int again = sweep();
        assertThat(paymentRepository.findById(paymentId).orElseThrow().getMercadoPagoId())
                .as("o id do gateway nao foi reescrito")
                .isEqualTo(gatewayId);
        assertThat(again).as("nada mais a liquidar deste teste").isZero();
    }

    @Test
    void skipsChargesTooRecentToWorryAbout() {
        approve(paymentId());

        // the sweep window ends an hour ago, so a charge opened seconds ago is not in it
        int settled = reconciliationJob.sweep(
                OffsetDateTime.now().minusDays(1), OffsetDateTime.now().minusHours(1));

        assertThat(settled).isZero();
        assertThat(orderRepository.findById(orderId).orElseThrow().getStatus())
                .isEqualTo(OrderStatus.PENDING);
    }

    @Test
    void oneUnreachableChargeDoesNotAbortTheSweep() {
        UUID paymentId = paymentId();
        GatewayWithApprovals.UNREACHABLE.add(paymentId);

        // the sweep has to survive it: aborting would strand every later charge in the batch
        sweep();
        assertThat(paymentRepository.findById(paymentId).orElseThrow().isPaid()).isFalse();
    }

    // ---------- helpers ----------

    private UUID paymentId() {
        return orderRepository.findById(orderId).orElseThrow().getPaymentId();
    }

    /**
     * Unique per run: {@code payments.mercado_pago_id} is unique, and the database outlives
     * the test, so a fixed value settles once and violates the constraint on every run after.
     */
    private String approve(UUID paymentId) {
        String gatewayId = "mp-" + UUID.randomUUID();
        GatewayWithApprovals.APPROVED.put(paymentId, GatewayPayment.builder()
                .gatewayId(gatewayId)
                .externalReference(paymentId.toString())
                .status("approved")
                .build());
        return gatewayId;
    }

    /** Window wide enough to include a charge opened moments ago. */
    private int sweep() {
        return reconciliationJob.sweep(
                OffsetDateTime.now().minusDays(1), OffsetDateTime.now().plusMinutes(1));
    }
}
