package reiz.miniecommerce.modules.payments.adapters.out.mercadopago;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;

/**
 * Verifies that a webhook really came from Mercado Pago.
 *
 * <p>This endpoint is public and it settles orders. Without this check it is a "mark any
 * order as paid" button open to the internet, so a failed verification must stop the request
 * before anything else happens.
 *
 * <p>Mercado Pago sends {@code x-signature: ts=<millis>,v1=<hex>}. The signed manifest is
 * {@code id:<data.id>;request-id:<x-request-id>;ts:<ts>;} and {@code v1} is its HMAC-SHA256
 * under the webhook secret.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class MercadoPagoSignatureVerifier {

    private static final String ALGORITHM = "HmacSHA256";

    private final MercadoPagoProperties properties;

    public boolean isValid(String signatureHeader, String requestId, String dataId) {
        if (properties.getWebhookSecret() == null || properties.getWebhookSecret().isBlank()) {
            log.warn("Webhook secret is not configured; rejecting notification");
            return false;
        }
        if (signatureHeader == null || dataId == null) {
            return false;
        }

        String timestamp = part(signatureHeader, "ts");
        String signature = part(signatureHeader, "v1");
        if (timestamp == null || signature == null) {
            return false;
        }

        // Mercado Pago lowercases the id before signing it
        String manifest = "id:%s;request-id:%s;ts:%s;".formatted(dataId.toLowerCase(), requestId, timestamp);
        return constantTimeEquals(hmacHex(manifest), signature);
    }

    private String part(String header, String key) {
        for (String piece : header.split(",")) {
            String[] pair = piece.trim().split("=", 2);
            if (pair.length == 2 && pair[0].trim().equals(key)) {
                return pair[1].trim();
            }
        }
        return null;
    }

    private String hmacHex(String manifest) {
        try {
            Mac mac = Mac.getInstance(ALGORITHM);
            mac.init(new SecretKeySpec(
                    properties.getWebhookSecret().getBytes(StandardCharsets.UTF_8), ALGORITHM));
            return HexFormat.of().formatHex(mac.doFinal(manifest.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("Could not compute the webhook signature", e);
        }
    }

    /**
     * Compared byte by byte to the end regardless of where the first difference is: a
     * comparison that returns early leaks, through its own timing, how much of a guessed
     * signature was correct.
     */
    private boolean constantTimeEquals(String expected, String received) {
        return MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.UTF_8),
                received.getBytes(StandardCharsets.UTF_8));
    }
}
