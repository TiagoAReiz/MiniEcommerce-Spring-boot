package reiz.miniecommerce.modules.shipments.adapters.in.controllers;

import reiz.miniecommerce.modules.auth.application.services.CurrentUserProvider;
import reiz.miniecommerce.modules.shipments.adapters.in.dtos.CreateShipmentRequest;
import reiz.miniecommerce.modules.shipments.adapters.in.dtos.MarkShippedRequest;
import reiz.miniecommerce.modules.shipments.adapters.in.dtos.ShipmentResponse;
import reiz.miniecommerce.modules.shipments.application.services.ShipmentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequiredArgsConstructor
public class ShipmentController {

    private final ShipmentService shipmentService;
    private final CurrentUserProvider currentUser;

    @PostMapping("/orders/{orderId}/shipment")
    @PreAuthorize("hasRole('OWNER')")
    @ResponseStatus(HttpStatus.CREATED)
    public ShipmentResponse create(@PathVariable UUID orderId,
                                   @Valid @RequestBody(required = false) CreateShipmentRequest request) {
        return ShipmentResponse.from(shipmentService.createFor(
                orderId, request == null ? null : request.estimatedDeliveryAt()));
    }

    @GetMapping("/orders/{orderId}/shipment")
    public ShipmentResponse forOrder(@PathVariable UUID orderId) {
        return ShipmentResponse.from(
                shipmentService.forOrder(orderId, currentUser.requireId(), callerIsOwner()));
    }

    @PatchMapping("/shipments/{id}")
    @PreAuthorize("hasRole('OWNER')")
    public ShipmentResponse markShipped(@PathVariable UUID id,
                                        @Valid @RequestBody(required = false) MarkShippedRequest request) {
        return ShipmentResponse.from(shipmentService.markShipped(
                id, request == null ? null : request.shippedAt()));
    }

    private boolean callerIsOwner() {
        return SecurityContextHolder.getContext().getAuthentication().getAuthorities().stream()
                .anyMatch(authority -> "ROLE_OWNER".equals(authority.getAuthority()));
    }
}
