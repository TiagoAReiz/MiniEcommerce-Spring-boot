package reiz.miniecommerce.modules.payments.adapters.in.controllers;

import reiz.miniecommerce.modules.payments.adapters.out.mercadopago.MercadoPagoSignatureVerifier;
import reiz.miniecommerce.modules.payments.application.services.WebhookProcessor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Receives Mercado Pago notifications.
 *
 * <p>Public by necessity — the gateway has no token from us — which is exactly why the
 * signature check runs before anything else. This endpoint moves orders to PAID; unguarded,
 * it would let anyone settle any order.
 *
 * <p>Everything that passes the signature check answers 200 immediately, including
 * notifications that are duplicated, unknown, or about a payment that was not approved.
 * Mercado Pago retries anything that is not 2xx — and gives up waiting after a couple of
 * seconds — so both slow answers and error answers earn a redelivery loop.
 */
@RestController
@RequestMapping("/webhooks/mercado-pago")
@RequiredArgsConstructor
@Slf4j
public class MercadoPagoWebhookController {

    private final MercadoPagoSignatureVerifier signatureVerifier;
    private final WebhookProcessor webhookProcessor;

    @PostMapping
    public ResponseEntity<Void> receive(
            @RequestHeader(value = "x-signature", required = false) String signature,
            @RequestHeader(value = "x-request-id", required = false) String requestId,
            @RequestParam(value = "data.id", required = false) String dataIdParam,
            @RequestParam(value = "type", required = false) String type,
            @RequestBody(required = false) Map<String, Object> body) {

        String dataId = dataIdParam != null ? dataIdParam : dataIdFrom(body);

        if (!signatureVerifier.isValid(signature, requestId, dataId)) {
            log.warn("Rejected a Mercado Pago notification with an invalid signature");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        String notificationType = type != null ? type : String.valueOf(body == null ? "" : body.get("type"));
        if ("payment".equalsIgnoreCase(notificationType) && dataId != null) {
            // handed off so the response goes back in milliseconds: confirming the
            // payment means calling Mercado Pago, which takes seconds, and they give
            // up long before that and redeliver
            webhookProcessor.settle(dataId);
        } else {
            log.info("Ignoring Mercado Pago notification of type {}", notificationType);
        }

        return ResponseEntity.ok().build();
    }

    @SuppressWarnings("unchecked")
    private String dataIdFrom(Map<String, Object> body) {
        if (body == null) {
            return null;
        }
        Object data = body.get("data");
        if (data instanceof Map<?, ?> map) {
            Object id = ((Map<String, Object>) map).get("id");
            return id == null ? null : String.valueOf(id);
        }
        return null;
    }
}
