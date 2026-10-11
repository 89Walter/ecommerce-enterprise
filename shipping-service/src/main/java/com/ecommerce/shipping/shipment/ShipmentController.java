package com.ecommerce.shipping.shipment;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PagedModel;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@Tag(name = "Spedizioni", description = "Tracciamento e gestione delle spedizioni.")
@SecurityScheme(name = "bearerAuth", type = SecuritySchemeType.HTTP, scheme = "bearer", bearerFormat = "JWT")
public class ShipmentController {

    private final ShipmentService shipmentService;

    @Operation(
            summary = "Spedizione di un ordine",
            description = "Il cliente vede solo le proprie spedizioni, l'ADMIN tutte.",
            security = @SecurityRequirement(name = "bearerAuth")
    )
    @GetMapping("/orders/{orderId}")
    public ShipmentResponse findByOrder(@PathVariable Long orderId, JwtAuthenticationToken authentication) {
        return shipmentService.findByOrder(orderId, authentication.getName(), isAdmin(authentication));
    }

    @Operation(summary = "Elenca le spedizioni", description = "Riservato agli amministratori.",
            security = @SecurityRequirement(name = "bearerAuth"))
    @GetMapping("/admin")
    public PagedModel<ShipmentResponse> findAll(
            @Parameter(description = "Filtro opzionale per stato.")
            @RequestParam(required = false) ShipmentStatus status,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        return new PagedModel<>(shipmentService.findAll(status, pageable));
    }

    @Operation(
            summary = "Avanza lo stato di una spedizione",
            description = "Riservato agli amministratori: CREATED -> SHIPPED -> DELIVERED. Aggiorna anche l'ordine.",
            security = @SecurityRequirement(name = "bearerAuth")
    )
    @PatchMapping("/admin/{id}/status")
    public ShipmentResponse advance(@PathVariable Long id, @Valid @RequestBody ShipmentStatusRequest request) {
        return shipmentService.advance(id, request.status());
    }

    private static boolean isAdmin(JwtAuthenticationToken authentication) {
        return authentication.getAuthorities().stream()
                .anyMatch(authority -> "ROLE_ADMIN".equals(authority.getAuthority()));
    }
}
