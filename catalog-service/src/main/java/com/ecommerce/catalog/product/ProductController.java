package com.ecommerce.catalog.product;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PagedModel;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * API REST del catalogo. Tutte <b>sincrone</b>: il client invia la richiesta e aspetta la risposta HTTP,
 * che arriva solo quando l'operazione è conclusa. Il catalog non pubblica eventi su RabbitMQ.
 * <p>
 * Percorsi completi: gateway {@code http://localhost:8080} + context path {@code /api/v1/catalog} + {@code /products}.
 */
@RestController
@RequestMapping("/products")
@RequiredArgsConstructor
@Tag(name = "Prodotti", description = "Operazioni per la gestione del catalogo prodotti.")
@SecurityScheme(
        name = "bearerAuth",
        type = SecuritySchemeType.HTTP,
        scheme = "bearer",
        bearerFormat = "JWT"
)
public class ProductController {

    private final ProductService productService;

    /**
     * {@code POST /api/v1/catalog/products} — crea un prodotto. Solo ADMIN.
     * <p>
     * <b>SINCRONA</b>: valida il body, salva su MySQL e risponde 201 con il prodotto creato.
     * Nessun effetto asincrono.
     */
    @Operation(
            summary = "Crea un prodotto",
            description = "Crea un prodotto. Richiede un access token con ruolo ADMIN.",
            security = @SecurityRequirement(name = "bearerAuth")
    )
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Prodotto creato."),
            @ApiResponse(responseCode = "400", description = "Dati del prodotto non validi."),
            @ApiResponse(responseCode = "401", description = "Token mancante o non valido."),
            @ApiResponse(responseCode = "403", description = "Token valido, ma senza ruolo ADMIN.")
    })
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ProductResponse createProduct(@Valid @RequestBody ProductRequest request) {
        return productService.create(request);
    }

    /**
     * {@code GET /api/v1/catalog/products?search=&page=&size=&sort=} — elenco paginato con ricerca testuale. Pubblica.
     * <p>
     * <b>SINCRONA</b>: legge da MySQL (le liste non sono in cache) e risponde 200 con la pagina.
     */
    @Operation(
            summary = "Elenca i prodotti",
            description = "Restituisce i prodotti in pagine, con ricerca testuale opzionale."
    )
    @ApiResponse(responseCode = "200", description = "Pagina dei prodotti restituita.")
    @GetMapping
    public PagedModel<ProductResponse> findAll(
            @Parameter(description = "Testo da cercare tra i prodotti.")
            @RequestParam(required = false) String search,
            @PageableDefault(size = 20, sort = "id") Pageable pageable
    ) {
        Page<ProductResponse> products = productService.findAll(search, pageable);
        return new PagedModel<>(products);
    }

    /**
     * {@code GET /api/v1/catalog/products/{id}} — dettaglio di un prodotto. Pubblica.
     * <p>
     * <b>SINCRONA</b>: legge prima dalla cache Redis e, se manca, da MySQL. È anche l'API che l'order-service
     * chiama in modo sincrono (HTTP diretto, senza gateway) per leggere nome e prezzo durante la creazione di un ordine.
     */
    @Operation(
            summary = "Recupera un prodotto",
            description = "Restituisce i dettagli del prodotto identificato dall'ID."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Prodotto trovato."),
            @ApiResponse(responseCode = "404", description = "Prodotto non trovato.")
    })
    @GetMapping("/{id}")
    public ProductResponse findById(
            @Parameter(description = "ID del prodotto da recuperare.", example = "1")
            @PathVariable Long id
    ) {
        return productService.findById(id);
    }

    /**
     * {@code PUT /api/v1/catalog/products/{id}} — aggiorna nome, descrizione e prezzo. Solo ADMIN.
     * <p>
     * <b>SINCRONA</b>: salva su MySQL e aggiorna la cache Redis nella stessa richiesta.
     * Gli ordini già fatti non cambiano: contengono una copia (snapshot) del prezzo di allora.
     */
    @Operation(
            summary = "Aggiorna un prodotto",
            description = "Aggiorna i dati del prodotto identificato dall'ID. Richiede un access token con ruolo ADMIN.",
            security = @SecurityRequirement(name = "bearerAuth")
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Prodotto aggiornato."),
            @ApiResponse(responseCode = "400", description = "Dati del prodotto non validi."),
            @ApiResponse(responseCode = "404", description = "Prodotto non trovato."),
            @ApiResponse(responseCode = "401", description = "Token mancante o non valido."),
            @ApiResponse(responseCode = "403", description = "Token valido, ma senza ruolo ADMIN.")
    })
    @PutMapping("/{id}")
    public ProductResponse update(
            @Parameter(description = "ID del prodotto da aggiornare.", example = "1")
            @PathVariable Long id,
            @Valid @RequestBody ProductRequest request
    ) {
        return productService.update(id, request);
    }

    /**
     * {@code DELETE /api/v1/catalog/products/{id}} — elimina un prodotto. Solo ADMIN.
     * <p>
     * <b>SINCRONA</b>: cancella da MySQL, invalida la cache e risponde 204 senza corpo.
     */
    @Operation(
            summary = "Elimina un prodotto",
            description = "Elimina il prodotto identificato dall'ID. Richiede un access token con ruolo ADMIN.",
            security = @SecurityRequirement(name = "bearerAuth")
    )
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Prodotto eliminato."),
            @ApiResponse(responseCode = "404", description = "Prodotto non trovato."),
            @ApiResponse(responseCode = "401", description = "Token mancante o non valido."),
            @ApiResponse(responseCode = "403", description = "Token valido, ma senza ruolo ADMIN.")
    })
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(
            @Parameter(description = "ID del prodotto da eliminare.", example = "1")
            @PathVariable Long id
    ) {
        productService.delete(id);
    }
}
