package com.marquee.http;

import com.marquee.logging.AppLogger;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import java.io.IOException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

/** An explicit method/path router for the JDK HTTP server; no framework routing. */
public final class Router implements HttpHandler {
    private final AppLogger log;

    public Router(AppLogger log) {
        this.log = log;
    }
    private final List<Route> routes = new CopyOnWriteArrayList<>();

    /** Dynamic path segments use braces, e.g. {@code /films/{filmId}}. */
    public void add(String method, String path, Endpoint endpoint) {
        if (method == null || method.isBlank() || endpoint == null) {
            throw new IllegalArgumentException("method and endpoint are required");
        }
        List<String> pattern = splitPath(path);
        String normalizedMethod = method.toUpperCase(Locale.ROOT);
        if (routes.stream().anyMatch(route -> route.method.equals(normalizedMethod)
                && route.pattern.equals(pattern))) {
            throw new IllegalArgumentException("Duplicate route: " + normalizedMethod + " " + path);
        }
        routes.add(new Route(normalizedMethod, pattern, endpoint));
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        List<RouteMatch> matches = new ArrayList<>();
        for (Route route : routes) {
            Map<String, String> pathParameters = route.match(exchange.getRequestURI().getRawPath());
            if (pathParameters != null) {
                matches.add(new RouteMatch(route, pathParameters));
            }
        }
        if (matches.isEmpty()) {
            Responses.error(exchange, 404, "Route not found", List.of());
            return;
        }
        String requestMethod = exchange.getRequestMethod().toUpperCase(Locale.ROOT);
        for (RouteMatch match : matches) {
            if (match.route.method.equals(requestMethod)) {
                try {
                    match.route.endpoint.handle(exchange, match.pathParameters);
                } catch (ApiException exception) {
                    Responses.error(exchange, exception.statusCode(), exception.getMessage(), exception.details());
                } catch (RuntimeException exception) {
                    Responses.serviceError(exchange, exception);
                }
                return;
            }
        }
        String allowedMethods = matches.stream().map(match -> match.route.method).distinct().sorted()
                .collect(java.util.stream.Collectors.joining(", "));
        exchange.getResponseHeaders().set("Allow", allowedMethods);
        Responses.error(exchange, 405, "Method not allowed", List.of());
    }

    @FunctionalInterface
    public interface Endpoint {
        void handle(HttpExchange exchange, Map<String, String> pathParameters) throws IOException;
    }

    private record RouteMatch(Route route, Map<String, String> pathParameters) {}

    private record Route(String method, List<String> pattern, Endpoint endpoint) {
        private Map<String, String> match(String rawPath) {
            List<String> actual = splitPath(rawPath);
            if (pattern.size() != actual.size()) {
                return null;
            }
            Map<String, String> parameters = new LinkedHashMap<>();
            for (int index = 0; index < pattern.size(); index++) {
                String expected = pattern.get(index);
                String value = actual.get(index);
                if (expected.startsWith("{") && expected.endsWith("}")) {
                    String key = expected.substring(1, expected.length() - 1);
                    if (key.isBlank() || value.isEmpty()) {
                        return null;
                    }
                    try {
                        parameters.put(key, URLDecoder.decode(value.replace("+", "%2B"), StandardCharsets.UTF_8));
                    } catch (IllegalArgumentException exception) {
                        return null;
                    }
                } else if (!expected.equals(value)) {
                    return null;
                }
            }
            return Map.copyOf(parameters);
        }
    }

    private static List<String> splitPath(String path) {
        if (path == null || path.isEmpty() || path.equals("/")) {
            return List.of();
        }
        String normalized = path.startsWith("/") ? path.substring(1) : path;
        if (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized.isEmpty() ? List.of() : List.of(normalized.split("/", -1));
    }
}
