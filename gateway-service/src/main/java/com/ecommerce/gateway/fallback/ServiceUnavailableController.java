package com.ecommerce.gateway.fallback;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;

import java.time.Instant;

@RestController
@RequestMapping("/fallback")
public class ServiceUnavailableController {

    @RequestMapping("/catalog")
    public ResponseEntity<GatewayErrorResponse> catalogUnavailable(ServerWebExchange exchange) {
        return unavailable("catalog-service", exchange);
    }

    @RequestMapping("/orders")
    public ResponseEntity<GatewayErrorResponse> ordersUnavailable(ServerWebExchange exchange) {
        return unavailable("order-service", exchange);
    }

    @RequestMapping("/users")
    public ResponseEntity<GatewayErrorResponse> usersUnavailable(ServerWebExchange exchange) {
        return unavailable("user-service", exchange);
    }

    @RequestMapping("/payments")
    public ResponseEntity<GatewayErrorResponse> paymentsUnavailable(ServerWebExchange exchange) {
        return unavailable("payment-service", exchange);
    }

    @RequestMapping("/shipments")
    public ResponseEntity<GatewayErrorResponse> shipmentsUnavailable(ServerWebExchange exchange) {
        return unavailable("shipping-service", exchange);
    }

    private ResponseEntity<GatewayErrorResponse> unavailable(
            String service,
            ServerWebExchange exchange
    ) {
        GatewayErrorResponse response = new GatewayErrorResponse(
                Instant.now(),
                HttpStatus.SERVICE_UNAVAILABLE.value(),
                "Service temporarily unavailable",
                service,
                exchange.getRequest().getPath().value()
        );
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(response);
    }

    public record GatewayErrorResponse(
            Instant timestamp,
            int status,
            String error,
            String service,
            String path
    ) {}
}
