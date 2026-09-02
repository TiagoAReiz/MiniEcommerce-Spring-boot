package reiz.miniecommerce.modules.payments.application.services;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/**
 * Runs webhook settlement off the request thread.
 *
 * <p>Settling a payment means calling the provider's API to confirm it, which is a network
 * round trip measured in seconds. Doing that before answering makes the provider time out
 * and redeliver, and a redelivery storm is worse than a late settlement.
 *
 * <p>So the controller answers as soon as the signature checks out, and the actual work
 * happens here. The cost of that choice: once 200 is sent, the provider will not retry on
 * our behalf, so a failure here is logged loudly and the idempotency claim is given back —
 * a later notification for the same payment can then still settle it.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class WebhookProcessor {

    private final PaymentService paymentService;

    @Async
    public void settle(String gatewayPaymentId) {
        try {
            paymentService.settleFromNotification(gatewayPaymentId);
        } catch (RuntimeException e) {
            log.error("Could not settle payment {} from its notification; the claim was released "
                    + "so a later notification can retry", gatewayPaymentId, e);
        }
    }
}
