package reiz.miniecommerce.modules.payments;

import reiz.miniecommerce.modules.payments.core.entities.GatewayPayment;
import reiz.miniecommerce.modules.payments.core.entities.PaymentIntent;
import reiz.miniecommerce.modules.payments.core.exceptions.PaymentGatewayException;
import reiz.miniecommerce.modules.payments.core.interfaces.repositories.NotificationDeduplicator;
import reiz.miniecommerce.modules.payments.core.interfaces.repositories.PaymentGateway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Guards the two failure modes that would silently lose a payment.
 */
@SpringBootTest
class WebhookResilienceTest {

    /** A gateway whose behaviour each test dictates. */
    @TestConfiguration
    static class FlakyGateway {

        static final AtomicReference<RuntimeException> FAIL_WITH = new AtomicReference<>();
        static final AtomicReference<GatewayPayment> RETURNS = new AtomicReference<>();
        static final AtomicInteger CALLS = new AtomicInteger();

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
                    CALLS.incrementAndGet();
                    RuntimeException boom = FAIL_WITH.get();
                    if (boom != null) {
                        throw boom;
                    }
                    return Optional.ofNullable(RETURNS.get());
                }

                @Override
                public Optional<GatewayPayment> findByExternalReference(UUID paymentId) {
                    return Optional.empty();
                }
            };
        }
    }

    @Autowired private reiz.miniecommerce.modules.payments.application.services.PaymentService paymentService;
    @Autowired private NotificationDeduplicator deduplicator;

    private String notificationId;

    @BeforeEach
    void reset() {
        FlakyGateway.FAIL_WITH.set(null);
        FlakyGateway.RETURNS.set(null);
        FlakyGateway.CALLS.set(0);
        notificationId = "mp-" + UUID.randomUUID();
    }

    @Test
    void aPaymentTheGatewayDoesNotKnowIsAcceptedQuietly() {
        FlakyGateway.RETURNS.set(null);

        paymentService.settleFromNotification(notificationId);

        assertThat(FlakyGateway.CALLS.get()).isEqualTo(1);
    }

    @Test
    void aFailedRunGivesTheClaimBackSoTheRetryCanStillSettleIt() {
        FlakyGateway.FAIL_WITH.set(new PaymentGatewayException("gateway fora do ar", null));

        assertThatThrownBy(() -> paymentService.settleFromNotification(notificationId))
                .isInstanceOf(PaymentGatewayException.class);

        // the claim must be back, or every redelivery would be dismissed as a duplicate and
        // the payment would never settle
        assertThat(deduplicator.claim(notificationId))
                .as("claim devolvida apos a falha")
                .isTrue();
        deduplicator.release(notificationId);

        // and the retry now goes through
        FlakyGateway.FAIL_WITH.set(null);
        paymentService.settleFromNotification(notificationId);
        assertThat(FlakyGateway.CALLS.get()).isEqualTo(2);
    }

    @Test
    void aSuccessfulRunKeepsTheClaimSoARetryIsIgnored() {
        FlakyGateway.RETURNS.set(null);

        paymentService.settleFromNotification(notificationId);
        paymentService.settleFromNotification(notificationId);

        // second call short-circuits on the claim and never reaches the gateway
        assertThat(FlakyGateway.CALLS.get()).isEqualTo(1);
    }
}
