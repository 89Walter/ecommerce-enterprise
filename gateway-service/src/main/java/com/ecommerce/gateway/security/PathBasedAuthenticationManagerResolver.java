package com.ecommerce.gateway.security;

import org.springframework.security.authentication.ReactiveAuthenticationManager;
import org.springframework.security.authentication.ReactiveAuthenticationManagerResolver;
import org.springframework.security.oauth2.server.resource.InvalidBearerTokenException;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.Map;

/**
 * Sceglie il validatore JWT in base al prefisso del percorso: ogni microservizio richiede
 * la propria audience nel token.
 */
public class PathBasedAuthenticationManagerResolver implements ReactiveAuthenticationManagerResolver<ServerWebExchange> {

    private static final ReactiveAuthenticationManager REJECT_ALL = authentication ->
            Mono.error(new InvalidBearerTokenException("Nessun servizio corrisponde al percorso richiesto."));

    private final Map<String, ? extends ReactiveAuthenticationManager> managersByPathPrefix;

    public PathBasedAuthenticationManagerResolver(Map<String, ? extends ReactiveAuthenticationManager> managersByPathPrefix) {
        this.managersByPathPrefix = Map.copyOf(managersByPathPrefix);
    }

    @Override
    public Mono<ReactiveAuthenticationManager> resolve(ServerWebExchange exchange) {
        String path = exchange.getRequest().getPath().pathWithinApplication().value();
        return Mono.just(managersByPathPrefix.entrySet().stream()
                .filter(entry -> matches(path, entry.getKey()))
                .<ReactiveAuthenticationManager>map(Map.Entry::getValue)
                .findFirst()
                .orElse(REJECT_ALL));
    }

    private static boolean matches(String path, String prefix) {
        String base = prefix.endsWith("/") ? prefix.substring(0, prefix.length() - 1) : prefix;
        return path.equals(base) || path.startsWith(base + "/");
    }
}
