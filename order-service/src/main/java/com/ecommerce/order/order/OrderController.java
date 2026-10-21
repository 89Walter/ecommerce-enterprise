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
 * API REST degli ordini, esposte direttamente sotto il context path /api/v1/orders.
 * <p>
 * Tutte le API HTTP sono <b>sincrone</b> (il client aspetta la risposta), ma creazione e annullamento
 * <b>avviano processi asincroni</b>: salvano un evento nell'outbox e il resto (pagamento, conferma,
 * spedizione, rimborso) avviene dopo, tramite RabbitMQ. Gli stati successivi alla creazione non si
 * cambiano a mano: li fanno avanzare gli eventi di pagamento e spedizione (vedi {@link OrderWorkflowService}
 * e le API asincrone in {@link OrderEventListeners}).
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

    /**
     * {@code POST /api/v1/orders} — crea un ordine per l'utente autenticato.
     * <p>
     * <b>SINCRONA</b> per il client, che riceve subito 201 con l'ordine in stato <b>PLACED</b>. Nel frattempo:
     * <ol>
     *     <li>chiama il catalog in modo <b>sincrono</b> (HTTP, timeout 2s/3s) per leggere nome e prezzo di ogni prodotto;</li>
     *     <li>salva in un'unica transazione l'ordine e l'evento {@code order.placed} nella tabella outbox.</li>
     * </ol>
     * <b>Effetto ASINCRONO</b>: l'OutboxRelay pubblica {@code order.placed} su RabbitMQ; il payment-service
     * addebita e risponde con {@code payment.completed} o {@code payment.failed}, che porteranno l'ordine
     * in CONFIRMED o CANCELLED dopo qualche istante. Per questo la risposta è PLACED e non CONFIRMED:
     * il frontend aggiorna la pagina dell'ordine finché lo stato non cambia.
     */
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
        return orderService.create(jwt.getSubject(), CustomerContact.from(jwt), request);
    }

    /**
     * {@code GET /api/v1/orders?page=&size=} — gli ordini dell'utente autenticato, dal più recente.
     * <p>
     * <b>SINCRONA</b>: legge da MySQL filtrando per l'utente del token (claim "sub"). Nessun effetto asincrono.
     */
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

    /**
     * {@code GET /api/v1/orders/admin?status=} — tutti gli ordini, con filtro opzionale per stato. Solo ADMIN.
     * <p>
     * <b>SINCRONA</b>: sola lettura da MySQL.
     */
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

    /**
     * {@code GET /api/v1/orders/{id}} — un ordine dell'utente autenticato (404 se è di un altro cliente).
     * <p>
     * <b>SINCRONA</b>: sola lettura. Restituisce lo stato attuale, che può cambiare tra due chiamate
     * per effetto degli eventi asincroni (pagamento, spedizione).
     */
    @Operation(summary = "Recupera un ordine dell'utente autenticato", security = @SecurityRequirement(name = "bearerAuth"))
    @GetMapping("/{id}")
    public OrderResponse findById(
            @AuthenticationPrincipal Jwt jwt,
            @Parameter(description = "ID dell'ordine da recuperare.")
            @PathVariable Long id
    ) {
        return orderService.findById(jwt.getSubject(), id);
    }

    /**
     * {@code POST /api/v1/orders/{id}/cancel} — annulla un ordine in stato PLACED o CONFIRMED (409 dopo la spedizione).
     * <p>
     * <b>SINCRONA</b>: l'ordine è già CANCELLED nella risposta. Nella stessa transazione salva l'evento
     * {@code order.cancelled} nell'outbox.
     * <b>Effetto ASINCRONO</b>: il payment-service rimborsa (se aveva addebitato) e pubblica {@code payment.refunded};
     * lo shipping-service annulla la spedizione non ancora partita; il notification-service manda le email
     * "ordine annullato" e "rimborso".
     */
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
