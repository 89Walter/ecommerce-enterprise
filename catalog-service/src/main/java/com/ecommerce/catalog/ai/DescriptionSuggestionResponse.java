package com.ecommerce.catalog.ai;

/** Proposta da mostrare all'amministratore, accanto alla descrizione attuale. Non è ancora salvata. */
public record DescriptionSuggestionResponse(Long productId, String currentDescription, String suggestedDescription) {}
