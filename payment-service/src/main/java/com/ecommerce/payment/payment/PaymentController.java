package com.ecommerce.payment.payment;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PagedModel;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * API REST dei pagamenti: solo letture <b>sincrone</b>. I pagamenti nascono e cambiano stato soltanto
 * tramite le API asincrone di {@link PaymentEventListeners} (eventi degli ordini su RabbitMQ):
 * non esiste un endpoint HTTP per "pagare".
 */
@RestController
@RequiredArgsConstructor
@Tag(name = "Pagamenti", description = "Consultazione dei pagamenti degli ordini.")
@SecurityScheme(name = "bearerAuth", type = SecuritySchemeType.HTTP, scheme = "bearer", bearerFormat = "JWT")
public class PaymentController {

    private final PaymentQueryService paymentQueryService;

    /**
     * {@code GET /api/v1/payments/orders/{orderId}} — il pagamento di un ordine. Proprietario dell'ordine o ADMIN.
     * <p>
     * <b>SINCRONA</b>: sola lettura. Subito dopo la creazione dell'ordine può rispondere 404: il pagamento
     * viene creato in modo asincrono, qualche istante dopo, quando arriva l'evento {@code order.placed}.
     */
    @Operation(
            summary = "Pagamento di un ordine",
            description = "Il cliente vede solo i propri pagamenti, l'ADMIN tutti.",
            security = @SecurityRequirement(name = "bearerAuth")
    )
    @GetMapping("/orders/{orderId}")
    public PaymentResponse findByOrder(@PathVariable Long orderId, JwtAuthenticationToken authentication) {
        return paymentQueryService.findByOrder(orderId, authentication.getName(), isAdmin(authentication));
    }

    /**
     * {@code GET /api/v1/payments/admin?status=} — tutti i pagamenti, con filtro opzionale per stato. Solo ADMIN.
     * <p>
     * <b>SINCRONA</b>: sola lettura.
     */
    @Operation(
            summary = "Elenca tutti i pagamenti",
            description = "Riservato agli amministratori.",
            security = @SecurityRequirement(name = "bearerAuth")
    )
    @GetMapping("/admin")
    public PagedModel<PaymentResponse> findAll(
            @Parameter(description = "Filtro opzionale per stato.")
            @RequestParam(required = false) PaymentStatus status,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        return new PagedModel<>(paymentQueryService.findAll(status, pageable));
    }

    private static boolean isAdmin(JwtAuthenticationToken authentication) {
        return authentication.getAuthorities().stream()
                .anyMatch(authority -> "ROLE_ADMIN".equals(authority.getAuthority()));
    }
}
