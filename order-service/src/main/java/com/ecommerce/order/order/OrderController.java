package com.ecommerce.order.order;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PagedModel;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Gli endpoint sono esposti direttamente sotto il context path /api/v1/orders.
 * Gli stati successivi alla creazione non si cambiano più a mano: li fanno avanzare
 * gli eventi di pagamento e spedizione (vedi {@link OrderWorkflowService}).
 */
@RestController
@RequiredArgsConstructor
@Tag(name = "Ordini", description = "Creazione e gestione degli ordini del cliente.")
@SecurityScheme(
        name = "bearerAuth",
        type = SecuritySchemeType.HTTP,
        scheme = "bearer",
        bearerFormat = "JWT"
)
public class OrderController {

    private final OrderService orderService;

    @Operation(
            summary = "Crea un ordine",
            description = "Crea l'ordine in stato PLACED; il pagamento parte in modo asincrono.",
            security = @SecurityRequirement(name = "bearerAuth")
    )
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Ordine creato."),
            @ApiResponse(responseCode = "400", description = "Dati non validi."),
            @ApiResponse(responseCode = "422", description = "Prodotto inesistente o ordine non valido."),
            @ApiResponse(responseCode = "502", description = "Catalog non disponibile.")
    })
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public OrderResponse create(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody CreateOrderRequest request
    ) {
        return orderService.create(jwt.getSubject(), request);
    }

    @Operation(summary = "Elenca gli ordini dell'utente autenticato", security = @SecurityRequirement(name = "bearerAuth"))
    @GetMapping
    public PagedModel<OrderResponse> findAll(
            @AuthenticationPrincipal Jwt jwt,
            @PageableDefault(size = 20, sort = {"createdAt", "id"}, direction = Sort.Direction.DESC)
            Pageable pageable
    ) {
        Page<OrderResponse> orders = orderService.findAllForCustomer(jwt.getSubject(), pageable);
        return new PagedModel<>(orders);
    }

    @Operation(
            summary = "Elenca tutti gli ordini",
            description = "Operazione riservata agli amministratori, con filtro opzionale per stato.",
            security = @SecurityRequirement(name = "bearerAuth")
    )
    @GetMapping("/admin")
    public PagedModel<OrderResponse> findAllForAdmin(
            @Parameter(description = "Stato degli ordini da mostrare.")
            @RequestParam(required = false) OrderStatus status,
            @PageableDefault(size = 20, sort = {"createdAt", "id"}, direction = Sort.Direction.DESC)
            Pageable pageable
    ) {
        return new PagedModel<>(orderService.findAll(status, pageable));
    }

    @Operation(summary = "Recupera un ordine dell'utente autenticato", security = @SecurityRequirement(name = "bearerAuth"))
    @GetMapping("/{id}")
    public OrderResponse findById(
            @AuthenticationPrincipal Jwt jwt,
            @Parameter(description = "ID dell'ordine da recuperare.")
            @PathVariable Long id
    ) {
        return orderService.findById(jwt.getSubject(), id);
    }

    @Operation(
            summary = "Annulla un ordine prima della spedizione",
            description = "Possibile negli stati PLACED e CONFIRMED; il pagamento viene rimborsato in modo asincrono.",
            security = @SecurityRequirement(name = "bearerAuth")
    )
    @PostMapping("/{id}/cancel")
    public OrderResponse cancel(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long id,
            @Valid @RequestBody CancelOrderRequest request
    ) {
        return orderService.cancel(jwt.getSubject(), id, request.reason());
    }
}
