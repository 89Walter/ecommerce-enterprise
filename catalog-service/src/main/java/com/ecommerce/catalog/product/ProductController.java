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
