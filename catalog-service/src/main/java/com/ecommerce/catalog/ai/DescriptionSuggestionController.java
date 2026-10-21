package com.ecommerce.catalog.ai;

import com.ecommerce.catalog.product.ProductResponse;
import com.ecommerce.catalog.product.ProductService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Generatore di schede prodotto. POST perché ogni chiamata produce un testo nuovo (e ha un costo):
 * non è una lettura ripetibile. Accesso riservato ad ADMIN dalla SecurityConfiguration (scrittura su /products/**).
 */
@RestController
@RequestMapping("/products")
@RequiredArgsConstructor
@Tag(name = "Prodotti - AI", description = "Proposte di descrizione generate con Claude.")
public class DescriptionSuggestionController {

    private final ProductService productService;
    private final ProductDescriptionWriter productDescriptionWriter;

    /**
     * {@code POST /api/v1/catalog/products/{id}/description-suggestion} — proposta di descrizione scritta da Claude. Solo ADMIN.
     * <p>
     * <b>SINCRONA</b>: la richiesta resta aperta mentre il catalog chiama l'API di Anthropic (HTTP sincrono verso
     * Internet) e risponde solo quando il testo è pronto, di solito in alcuni secondi. Per questo il gateway le dà
     * un timeout di 70 secondi invece dei 5 delle altre rotte. Non salva nulla e non pubblica eventi.
     */
    @Operation(
            summary = "Proponi una descrizione per il prodotto",
            description = "Genera con Claude una nuova descrizione a partire da nome, descrizione attuale e prezzo. "
                    + "La proposta non viene salvata: per usarla, invia la normale modifica del prodotto. "
                    + "Richiede un access token con ruolo ADMIN.",
            security = @SecurityRequirement(name = "bearerAuth")
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Proposta generata."),
            @ApiResponse(responseCode = "404", description = "Prodotto non trovato."),
            @ApiResponse(responseCode = "403", description = "Token valido, ma senza ruolo ADMIN."),
            @ApiResponse(responseCode = "503", description = "Generazione non configurata o modello non disponibile.")
    })
    @PostMapping("/{id}/description-suggestion")
    public DescriptionSuggestionResponse suggest(
            @Parameter(description = "ID del prodotto.", example = "1") @PathVariable Long id
    ) {
        ProductResponse product = productService.findById(id);
        return new DescriptionSuggestionResponse(
                product.id(),
                product.description(),
                productDescriptionWriter.suggestDescription(product)
        );
    }
}
