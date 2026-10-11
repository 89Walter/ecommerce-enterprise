package com.ecommerce.order.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.time.Clock;
import java.time.Duration;

@Configuration
public class CatalogClientConfiguration {

    /** Timeout espliciti: se il Catalog non risponde, la creazione dell'ordine fallisce con 502 invece di restare appesa. */
    @Bean
    RestClient catalogRestClient(
            @Value("${catalog-service.base-url}") String catalogBaseUrl,
            @Value("${catalog-service.connect-timeout:2s}") Duration connectTimeout,
            @Value("${catalog-service.read-timeout:3s}") Duration readTimeout
    ) {
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(connectTimeout)
                .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(readTimeout);
        return RestClient.builder()
                .baseUrl(catalogBaseUrl)
                .requestFactory(requestFactory)
                .build();
    }

    @Bean
    Clock orderClock() {
        return Clock.systemUTC();
    }
}
