package reiz.miniecommerce.modules.payments.adapters.in.controllers;

import reiz.miniecommerce.modules.auth.application.services.CurrentUserProvider;
import reiz.miniecommerce.modules.payments.adapters.in.dtos.CheckoutLinkResponse;
import reiz.miniecommerce.modules.payments.adapters.in.dtos.PaymentResponse;
import reiz.miniecommerce.modules.payments.application.services.PaymentService;
import reiz.miniecommerce.modules.payments.core.entities.OpenedCharge;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentService paymentService;
    private final CurrentUserProvider currentUser;

    @PostMapping("/orders/{orderId}/payments")
    @ResponseStatus(HttpStatus.CREATED)
    public CheckoutLinkResponse openCharge(@PathVariable UUID orderId) {
        OpenedCharge charge = paymentService.openCharge(
                orderId, currentUser.requireId(), callerIsOwner());

        return CheckoutLinkResponse.of(charge.intent(), charge.payment().getAmount());
    }

    @GetMapping("/payments/{id}")
    public PaymentResponse detail(@PathVariable UUID id) {
        return PaymentResponse.from(
                paymentService.visiblePayment(id, currentUser.requireId(), callerIsOwner()));
    }

    private boolean callerIsOwner() {
        return SecurityContextHolder.getContext().getAuthentication().getAuthorities().stream()
                .anyMatch(authority -> "ROLE_OWNER".equals(authority.getAuthority()));
    }
}
