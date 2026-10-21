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

    private final String prefix;

    public RateLimitKeyResolver() {
        this("");
    }

    /**
     * @param scope distingue i contatori di limiti diversi applicati alla stessa richiesta (es. "ai"):
     *              Redis usa la chiave come nome del contatore, quindi due limiti con la stessa chiave
     *              consumerebbero gettoni dallo stesso secchiello.
     */
    public RateLimitKeyResolver(String scope) {
        this.prefix = scope == null || scope.isBlank() ? "" : scope + ":";
    }

    @Override
    public Mono<String> resolve(ServerWebExchange exchange) {
        return exchange.getPrincipal()
                .map(Principal::getName)
                .filter(name -> !name.isBlank())
                .map(name -> prefix + "user:" + name)
                .switchIfEmpty(Mono.fromSupplier(() -> prefix + clientIp(exchange)));
    }

    private static String clientIp(ServerWebExchange exchange) {
        InetSocketAddress remoteAddress = exchange.getRequest().getRemoteAddress();
        if (remoteAddress == null || remoteAddress.getAddress() == null) {
            return UNKNOWN_CLIENT;
        }
        return "ip:" + remoteAddress.getAddress().getHostAddress();
    }
}
