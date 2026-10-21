package com.ecommerce.assistant.tools;

import com.ecommerce.assistant.config.AssistantProperties;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.net.URI;
import java.net.http.HttpClient;

/**
 * Chiama le API dei microservizi <b>con il token dell'utente</b> che sta chattando.
 * L'agente non ha quindi permessi propri: vede solo ciò che l'utente vedrebbe dal sito
 * (i suoi ordini, i suoi pagamenti) e i controlli restano quelli dei servizi.
 */
@Component
public class ShopApiClient {

    private final RestClient restClient;

    public ShopApiClient(AssistantProperties properties) {
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(properties.services().timeout())
                .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(properties.services().timeout());
        this.restClient = RestClient.builder().requestFactory(requestFactory).build();
    }

    /** @return il corpo JSON della risposta, oppure un esito di errore leggibile dal modello. */
    public ApiResult get(URI uri, String bearerToken) {
        try {
            String body = restClient.get()
                    .uri(uri)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + bearerToken)
                    .accept(MediaType.APPLICATION_JSON)
                    .retrieve()
                    .body(String.class);
            return ApiResult.ok(body == null ? "{}" : body);
        } catch (RestClientResponseException exception) {
            return ApiResult.error(describe(exception.getStatusCode()));
        } catch (RestClientException exception) {
            return ApiResult.error("Il servizio non è raggiungibile in questo momento.");
        }
    }

    private static String describe(HttpStatusCode status) {
        return switch (status.value()) {
            case 404 -> "Non trovato (oppure non appartiene all'utente).";
            case 401, 403 -> "L'utente non ha i permessi per vedere questi dati.";
            default -> "Il servizio ha risposto con un errore (HTTP " + status.value() + ").";
        };
    }

    public record ApiResult(boolean success, String content) {

        static ApiResult ok(String content) {
            return new ApiResult(true, content);
        }

        static ApiResult error(String message) {
            return new ApiResult(false, message);
        }
    }
}
