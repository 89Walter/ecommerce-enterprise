package com.ecommerce.gateway.security;

import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.net.InetSocketAddress;
import java.security.Principal;

/**
 * Chiave del rate limiting: l'utente del token se la richiesta è autenticata, altrimenti l'IP del client.
 * L'IP viene dalla connessione TCP e non da X-Forwarded-For, che il client può falsificare a piacere:
 * dietro un load balancer va configurato server.forward-headers-strategy, così Spring lo ricava in modo sicuro.
 */
public class RateLimitKeyResolver implements KeyResolver {

    static final String UNKNOWN_CLIENT = "ip:unknown";

    @Override
    public Mono<String> resolve(ServerWebExchange exchange) {
        return exchange.getPrincipal()
                .map(Principal::getName)
                .filter(name -> !name.isBlank())
                .map(name -> "user:" + name)
                .switchIfEmpty(Mono.fromSupplier(() -> clientIp(exchange)));
    }

    private static String clientIp(ServerWebExchange exchange) {
        InetSocketAddress remoteAddress = exchange.getRequest().getRemoteAddress();
        if (remoteAddress == null || remoteAddress.getAddress() == null) {
            return UNKNOWN_CLIENT;
        }
        return "ip:" + remoteAddress.getAddress().getHostAddress();
    }
}
