package com.ecommerce.order.catalog;

import com.ecommerce.order.exception.CatalogProductNotFoundException;
import com.ecommerce.order.exception.CatalogServiceException;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Component
public class CatalogProductClient {

    private final RestClient restClient;

    public CatalogProductClient(RestClient catalogRestClient) {
        this.restClient = catalogRestClient;
    }

    public CatalogProductResponse findById(Long productId) {
        try {
            CatalogProductResponse product = restClient.get()
                    .uri("/products/{id}", productId)
                    .retrieve()
                    .body(CatalogProductResponse.class);
            if (product == null) {
                throw new CatalogServiceException("Il Catalog ha restituito una risposta vuota.");
            }
            return product;
        } catch (HttpClientErrorException.NotFound exception) {
            throw new CatalogProductNotFoundException(productId);
        } catch (RestClientException exception) {
            throw new CatalogServiceException("Impossibile recuperare il prodotto " + productId + " dal Catalog.");
        }
    }
}
