package com.marquee.http;

import com.marquee.exceptions.DuplicateGenreException;
import com.marquee.exceptions.FilmNotFoundException;
import com.marquee.exceptions.GenreNotFoundException;
import com.marquee.exceptions.RepositoryException;
import com.marquee.exceptions.ScreeningNotFoundException;
import com.marquee.exceptions.ValidationException;
import com.sun.net.httpserver.HttpExchange;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/** Shared response serialization and translation from service exceptions to HTTP. */
public final class Responses {
    private static final Logger LOGGER = Logger.getLogger(Responses.class.getName());

    private Responses() {}

    public static void json(HttpExchange exchange, int statusCode, Object body) throws IOException {
        byte[] bytes = Json.stringify(body).getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        exchange.getResponseHeaders().set("X-Content-Type-Options", "nosniff");
        exchange.sendResponseHeaders(statusCode, bytes.length);
        try (var responseBody = exchange.getResponseBody()) {
            responseBody.write(bytes);
        }
    }

    public static void noContent(HttpExchange exchange) throws IOException {
        exchange.sendResponseHeaders(204, -1);
        exchange.close();
    }

    public static void error(HttpExchange exchange, int statusCode, String message, List<String> details)
            throws IOException {
        Map<String, Object> error = new LinkedHashMap<>();
        error.put("code", statusCode);
        error.put("message", message);
        if (details != null && !details.isEmpty()) {
            error.put("details", details);
        }
        json(exchange, statusCode, Map.of("error", error));
    }

    /** Maps only known service failures; unexpected repository failures stay 500s. */
    public static void serviceError(HttpExchange exchange, RuntimeException exception) throws IOException {
        if (exception instanceof ValidationException validationException) {
            error(exchange, 400, validationException.getMessage(), validationException.getDetails());
        } else if (exception instanceof DuplicateGenreException) {
            error(exchange, 409, exception.getMessage(), List.of());
        } else if (exception instanceof FilmNotFoundException
                || exception instanceof GenreNotFoundException
                || exception instanceof ScreeningNotFoundException) {
            error(exchange, 404, exception.getMessage(), List.of());
        } else if (exception instanceof RepositoryException) {
            LOGGER.log(Level.SEVERE, "Repository operation failed", exception);
            error(exchange, 500, "Internal server error", List.of());
        } else {
            LOGGER.log(Level.SEVERE, "Unexpected request failure", exception);
            error(exchange, 500, "Internal server error", List.of());
        }
    }
}
