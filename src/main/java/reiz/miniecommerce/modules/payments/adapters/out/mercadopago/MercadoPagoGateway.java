package reiz.miniecommerce.modules.payments.adapters.out.mercadopago;

import reiz.miniecommerce.modules.payments.core.entities.GatewayPayment;
import reiz.miniecommerce.modules.payments.core.entities.PaymentIntent;
import reiz.miniecommerce.modules.payments.core.exceptions.PaymentGatewayException;
import reiz.miniecommerce.modules.payments.core.interfaces.repositories.PaymentGateway;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Talks to Mercado Pago's Checkout Pro API.
 *
 * <p>Opening a charge creates a <em>preference</em>; the customer is then sent to the
 * {@code init_point} it returns. Our own payment id travels as {@code external_reference},
 * which is what lets an inbound notification — which carries only Mercado Pago's id — be
 * matched back to a row here.
 */
@Component
@Slf4j
public class MercadoPagoGateway implements PaymentGateway {

    private final RestClient restClient;
    private final MercadoPagoProperties properties;

    public MercadoPagoGateway(MercadoPagoProperties properties) {
        this.properties = properties;
        this.restClient = RestClient.builder()
                .baseUrl(properties.getBaseUrl())
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + properties.getAccessToken())
                .requestFactory(timeBoundedFactory(properties))
                .build();
    }

    /**
     * Bounds both phases of the call. Without a read timeout a gateway that accepts the
     * connection and then goes quiet holds the request thread until the container gives up.
     */
    private static SimpleClientHttpRequestFactory timeBoundedFactory(MercadoPagoProperties properties) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(properties.getTimeout());
        factory.setReadTimeout(properties.getTimeout());
        return factory;
    }

    @Override
    public PaymentIntent openCharge(UUID paymentId, String description, BigDecimal amount, String payerEmail) {
        Map<String, Object> preference = Map.of(
                "items", java.util.List.of(Map.of(
                        "title", description,
                        "quantity", 1,
                        "unit_price", amount,
                        "currency_id", "BRL")),
                "payer", Map.of("email", payerEmail == null ? "" : payerEmail),
                "external_reference", paymentId.toString(),
                "notification_url", properties.getNotificationUrl() == null ? "" : properties.getNotificationUrl(),
                "back_urls", Map.of(
                        "success", properties.getSuccessUrl(),
                        "failure", properties.getFailureUrl(),
                        "pending", properties.getPendingUrl()));

        try {
            Map<?, ?> response = restClient.post()
                    .uri("/checkout/preferences")
                    .contentType(MediaType.APPLICATION_JSON)
                    // Mercado Pago deduplicates on this key, so a retried call cannot open
                    // two charges for the same payment.
                    .header("X-Idempotency-Key", paymentId.toString())
                    .body(preference)
                    .retrieve()
                    .body(Map.class);

            if (response == null) {
                throw new PaymentGatewayException("Mercado Pago returned an empty preference", null);
            }

            return PaymentIntent.builder()
                    .gatewayReference(String.valueOf(response.get("id")))
                    .checkoutUrl(String.valueOf(response.get("init_point")))
                    .build();

        } catch (PaymentGatewayException e) {
            throw e;
        } catch (Exception e) {
            log.error("Could not create a Mercado Pago preference for payment {}", paymentId, e);
            throw new PaymentGatewayException("Could not open the charge at Mercado Pago", e);
        }
    }

    @Override
    public Optional<GatewayPayment> findPayment(String gatewayPaymentId) {
        try {
            Map<?, ?> response = restClient.get()
                    .uri("/v1/payments/{id}", gatewayPaymentId)
                    .retrieve()
                    .body(Map.class);

            if (response == null) {
                return Optional.empty();
            }

            return Optional.of(GatewayPayment.builder()
                    .gatewayId(String.valueOf(response.get("id")))
                    .externalReference(asString(response.get("external_reference")))
                    .status(asString(response.get("status")))
                    .build());

        } catch (HttpClientErrorException.NotFound e) {
            // Mercado Pago does not know this payment. Retrying will not change that, so this
            // is an absence, not a failure — the caller answers 200 and the retries stop.
            log.warn("Mercado Pago has no payment {}", gatewayPaymentId);
            return Optional.empty();

        } catch (Exception e) {
            log.error("Could not read payment {} from Mercado Pago", gatewayPaymentId, e);
            throw new PaymentGatewayException("Could not read the payment from Mercado Pago", e);
        }
    }

    /**
     * Uses the payment search endpoint, since the only handle a never-settled charge leaves
     * behind is the external reference we sent when opening it.
     */
    @Override
    public Optional<GatewayPayment> findByExternalReference(UUID paymentId) {
        try {
            Map<?, ?> response = restClient.get()
                    .uri(uri -> uri.path("/v1/payments/search")
                            .queryParam("external_reference", paymentId.toString())
                            .build())
                    .retrieve()
                    .body(Map.class);

            if (response == null || !(response.get("results") instanceof java.util.List<?> results)) {
                return Optional.empty();
            }

            // a charge can have several attempts against it; the approved one is what settles
            // the order, and any other outcome leaves it pending
            return results.stream()
                    .filter(Map.class::isInstance)
                    .map(Map.class::cast)
                    .map(this::toGatewayPayment)
                    .filter(GatewayPayment::isApproved)
                    .findFirst();

        } catch (Exception e) {
            log.error("Could not search payments for reference {} at Mercado Pago", paymentId, e);
            throw new PaymentGatewayException("Could not search the payment at Mercado Pago", e);
        }
    }

    private GatewayPayment toGatewayPayment(Map<?, ?> payment) {
        return GatewayPayment.builder()
                .gatewayId(asString(payment.get("id")))
                .externalReference(asString(payment.get("external_reference")))
                .status(asString(payment.get("status")))
                .build();
    }

    private String asString(Object value) {
        return value == null ? null : String.valueOf(value);
    }
}
