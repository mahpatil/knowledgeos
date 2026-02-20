package com.knowledgeos.infra;

import io.micronaut.context.annotation.Value;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.MutableHttpResponse;
import io.micronaut.http.annotation.Filter;
import io.micronaut.http.filter.HttpServerFilter;
import io.micronaut.http.filter.ServerFilterChain;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Mono;

import java.util.Map;

/**
 * Simple API-key authentication for all /api/v1/** endpoints.
 *
 * Skipped for: /swagger-ui/**, /ws/**, /health, and any path outside /api/v1/
 * Header: X-KOS-API-Key: {app.api-key}
 *
 * To disable (e.g. in tests), set {@code app.api-key} to an empty string.
 */
@Filter("/api/v1/**")
public class ApiKeyFilter implements HttpServerFilter {

    @Value("${app.api-key:dev-local-key}")
    String configuredKey;

    @Override
    public Publisher<MutableHttpResponse<?>> doFilter(HttpRequest<?> request,
                                                       ServerFilterChain chain) {
        // Disabled when key is blank (test environment)
        if (configuredKey == null || configuredKey.isBlank()) {
            return chain.proceed(request);
        }

        String provided = request.getHeaders().get("X-KOS-API-Key");
        if (configuredKey.equals(provided)) {
            return chain.proceed(request);
        }

        return Mono.just(HttpResponse.unauthorized()
            .body(Map.of("message", "Missing or invalid X-KOS-API-Key header")));
    }
}
