package com.ecommerce.assistant.tools;

import com.anthropic.core.JsonValue;
import com.anthropic.models.messages.Tool;
import com.ecommerce.assistant.config.AssistantProperties;
import com.ecommerce.assistant.tools.ShopApiClient.ApiResult;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Strumenti che Claude può usare per rispondere. Sono tutti <b>di sola lettura</b>:
 * l'assistente informa e consiglia, ma non crea né annulla ordini al posto dell'utente.
 * <p>
 * Le definizioni sono costanti e sempre nello stesso ordine: cambiarle a metà conversazione
 * invaliderebbe la cache del prompt (e il ragionamento già fatto dal modello).
 */
@Component
public class ShopTools {

    static final String SEARCH_PRODUCTS = "search_products";
    static final String GET_PRODUCT = "get_product";
    static final String LIST_MY_ORDERS = "list_my_orders";
    static final String GET_ORDER = "get_order";
    static final String GET_ORDER_PAYMENT = "get_order_payment";
    static final String GET_ORDER_SHIPMENT = "get_order_shipment";
    static final String GET_MY_PROFILE = "get_my_profile";

    /** Oltre questa dimensione il risultato viene troncato, per non riempire il contesto del modello. */
    private static final int MAX_RESULT_CHARS = 20_000;

    private final ShopApiClient shopApiClient;
    private final AssistantProperties.Services services;
    private final List<Tool> definitions;

    public ShopTools(ShopApiClient shopApiClient, AssistantProperties properties) {
        this.shopApiClient = shopApiClient;
        this.services = properties.services();
        this.definitions = List.of(
                tool(SEARCH_PRODUCTS,
                        "Cerca prodotti nel catalogo per parola chiave (nome o descrizione). Restituisce fino a 10 "
                                + "prodotti con id, nome, descrizione e prezzo in euro. Usa parole semplici e brevi: "
                                + "la ricerca è testuale, non semantica; se non trovi nulla riprova con un sinonimo.",
                        Map.of("query", Map.of("type", "string",
                                "description", "Parola o frase da cercare, per esempio \"caffè\" o \"cuffie\".")),
                        List.of("query")),
                tool(GET_PRODUCT,
                        "Restituisce i dettagli di un prodotto del catalogo dato il suo id.",
                        Map.of("product_id", Map.of("type", "integer", "description", "Id del prodotto.")),
                        List.of("product_id")),
                tool(LIST_MY_ORDERS,
                        "Elenca gli ultimi 10 ordini dell'utente, dal più recente, con stato, totale e righe.",
                        Map.of(),
                        List.of()),
                tool(GET_ORDER,
                        "Restituisce un ordine dell'utente: stato (PLACED, CONFIRMED, SHIPPED, DELIVERED, CANCELLED), "
                                + "righe, totale, indirizzo di spedizione, date dei passaggi di stato ed eventuale "
                                + "motivo di annullamento.",
                        Map.of("order_id", Map.of("type", "integer", "description", "Numero dell'ordine.")),
                        List.of("order_id")),
                tool(GET_ORDER_PAYMENT,
                        "Restituisce il pagamento di un ordine dell'utente: stato (COMPLETED, FAILED, REFUNDED, "
                                + "CANCELLED), importo ed eventuale motivo del rifiuto.",
                        Map.of("order_id", Map.of("type", "integer", "description", "Numero dell'ordine.")),
                        List.of("order_id")),
                tool(GET_ORDER_SHIPMENT,
                        "Restituisce la spedizione di un ordine dell'utente: stato (CREATED, SHIPPED, DELIVERED, "
                                + "CANCELLED), corriere e codice di tracking. Se non esiste, l'ordine non è ancora "
                                + "stato confermato.",
                        Map.of("order_id", Map.of("type", "integer", "description", "Numero dell'ordine.")),
                        List.of("order_id")),
                tool(GET_MY_PROFILE,
                        "Restituisce il profilo dell'utente: nome, email, telefono e indirizzo di spedizione predefinito.",
                        Map.of(),
                        List.of())
        );
    }

    public List<Tool> definitions() {
        return definitions;
    }

    /**
     * Esegue lo strumento richiesto dal modello. Gli errori (id mancante, ordine inesistente, servizio giù)
     * tornano al modello come risultato con {@code isError}, così può spiegarli all'utente o riprovare.
     */
    public ToolOutcome execute(String name, JsonValue rawInput, String bearerToken) {
        Map<String, Object> input = toMap(rawInput);
        try {
            URI uri = switch (name) {
                case SEARCH_PRODUCTS -> UriComponentsBuilder.fromUriString(services.catalogUrl())
                        .path("/products")
                        .queryParam("search", requireText(input, "query"))
                        .queryParam("size", 10)
                        .encode()
                        .build()
                        .toUri();
                case GET_PRODUCT -> uri(services.catalogUrl(), "/products/{id}", requireId(input, "product_id"));
                case LIST_MY_ORDERS -> UriComponentsBuilder.fromUriString(services.orderUrl())
                        .queryParam("size", 10)
                        .build()
                        .toUri();
                case GET_ORDER -> uri(services.orderUrl(), "/{id}", requireId(input, "order_id"));
                case GET_ORDER_PAYMENT -> uri(services.paymentUrl(), "/orders/{id}", requireId(input, "order_id"));
                case GET_ORDER_SHIPMENT -> uri(services.shipmentUrl(), "/orders/{id}", requireId(input, "order_id"));
                case GET_MY_PROFILE -> uri(services.userUrl(), "/me");
                default -> throw new IllegalArgumentException("Strumento sconosciuto: " + name);
            };
            ApiResult result = shopApiClient.get(uri, bearerToken);
            return new ToolOutcome(truncate(result.content()), !result.success());
        } catch (IllegalArgumentException exception) {
            return new ToolOutcome(exception.getMessage(), true);
        }
    }

    private static Tool tool(String name, String description, Map<String, Object> properties, List<String> required) {
        Tool.InputSchema.Properties.Builder schemaProperties = Tool.InputSchema.Properties.builder();
        new LinkedHashMap<>(properties).forEach((key, value) -> schemaProperties.putAdditionalProperty(key, JsonValue.from(value)));
        return Tool.builder()
                .name(name)
                .description(description)
                .strict(true)
                .inputSchema(Tool.InputSchema.builder()
                        .properties(schemaProperties.build())
                        .required(required)
                        .putAdditionalProperty("additionalProperties", JsonValue.from(false))
                        .build())
                .build();
    }

    private static URI uri(String baseUrl, String path, Object... variables) {
        return UriComponentsBuilder.fromUriString(baseUrl).path(path).buildAndExpand(variables).encode().toUri();
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> toMap(JsonValue rawInput) {
        try {
            Map<String, Object> map = rawInput.convert(Map.class);
            return map == null ? Map.of() : map;
        } catch (RuntimeException exception) {
            return Map.of();
        }
    }

    private static String requireText(Map<String, Object> input, String field) {
        Object value = input.get(field);
        if (!(value instanceof String text) || text.isBlank()) {
            throw new IllegalArgumentException("Parametro \"" + field + "\" mancante o vuoto.");
        }
        return text.trim();
    }

    private static long requireId(Map<String, Object> input, String field) {
        Object value = input.get(field);
        if (value instanceof Number number && number.longValue() > 0) {
            return number.longValue();
        }
        throw new IllegalArgumentException("Parametro \"" + field + "\" mancante o non valido: serve un numero intero positivo.");
    }

    private static String truncate(String content) {
        return content.length() <= MAX_RESULT_CHARS
                ? content
                : content.substring(0, MAX_RESULT_CHARS) + "... [risultato troncato]";
    }

    public record ToolOutcome(String content, boolean isError) {}
}
