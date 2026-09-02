package reiz.miniecommerce.modules.payments;

import reiz.miniecommerce.modules.payments.core.entities.GatewayPayment;
import reiz.miniecommerce.modules.payments.core.entities.PaymentIntent;
import reiz.miniecommerce.modules.payments.core.interfaces.repositories.PaymentGateway;
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
import java.time.Duration;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The webhook has to answer in milliseconds even when the gateway is slow.
 *
 * <p>This is the bug that broke the real integration: confirming a payment meant calling
 * Mercado Pago inline, the response took 10 to 14 seconds, and their simulator gave up before
 * it arrived — so the notification never even reached the tunnel.
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "app.mercado-pago.webhook-secret=segredo-de-latencia",
        "app.mercado-pago.access-token=test-token"
})
class WebhookLatencyTest {

    private static final String SECRET = "segredo-de-latencia";
    private static final Duration GATEWAY_DELAY = Duration.ofSeconds(3);

    @TestConfiguration
    static class SlowGateway {

        static final CountDownLatch CALLED = new CountDownLatch(1);

        @Bean
        @Primary
        PaymentGateway paymentGateway() {
            return new PaymentGateway() {
                @Override
                public PaymentIntent openCharge(UUID paymentId, String description,
                                                BigDecimal amount, String payerEmail) {
                    return PaymentIntent.builder().gatewayReference("pref").checkoutUrl("url").build();
                }

                @Override
                public Optional<GatewayPayment> findPayment(String gatewayPaymentId) {
                    CALLED.countDown();
                    try {
                        Thread.sleep(GATEWAY_DELAY.toMillis());
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                    return Optional.empty();
                }

                @Override
                public Optional<GatewayPayment> findByExternalReference(UUID paymentId) {
                    return Optional.empty();
                }
            };
        }
    }

    @Autowired
    private MockMvc mockMvc;

    @Test
    void answersImmediatelyEvenWhenTheGatewayTakesSeconds() throws Exception {
        String dataId = "mp-" + UUID.randomUUID();
        String requestId = "req-" + UUID.randomUUID();
        long ts = System.currentTimeMillis();

        long before = System.nanoTime();
        mockMvc.perform(post("/webhooks/mercado-pago")
                        .header("x-signature", signature(dataId, requestId, ts))
                        .header("x-request-id", requestId)
                        .param("data.id", dataId)
                        .param("type", "payment")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"payment\",\"data\":{\"id\":\"" + dataId + "\"}}"))
                .andExpect(status().isOk());
        Duration answered = Duration.ofNanos(System.nanoTime() - before);

        assertThat(answered)
                .as("resposta tem que voltar bem antes do gateway de %s", GATEWAY_DELAY)
                .isLessThan(Duration.ofSeconds(1));

        // and the work really did start, off the request thread
        assertThat(SlowGateway.CALLED.await(10, TimeUnit.SECONDS))
                .as("o processamento foi disparado em background")
                .isTrue();
    }

    private String signature(String dataId, String requestId, long ts) throws Exception {
        String manifest = "id:%s;request-id:%s;ts:%d;".formatted(dataId.toLowerCase(), requestId, ts);
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return "ts=%d,v1=%s".formatted(ts,
                HexFormat.of().formatHex(mac.doFinal(manifest.getBytes(StandardCharsets.UTF_8))));
    }
}
