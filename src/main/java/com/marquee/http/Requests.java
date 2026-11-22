package com.marquee.http;

import com.sun.net.httpserver.HttpExchange;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

/** Parses HTTP query strings; business validation remains the service's job. */
public final class Requests {
    private Requests() {}

    public static Map<String, String> queryParameters(HttpExchange exchange) {
        Map<String, String> parameters = new LinkedHashMap<>();
        String query = exchange.getRequestURI().getRawQuery();
        if (query == null || query.isEmpty()) {
            return Map.of();
        }
        for (String pair : query.split("&")) {
            if (pair.isEmpty()) {
                continue;
            }
            int separator = pair.indexOf('=');
            String rawKey = separator < 0 ? pair : pair.substring(0, separator);
            String rawValue = separator < 0 ? "" : pair.substring(separator + 1);
            try {
                String key = URLDecoder.decode(rawKey, StandardCharsets.UTF_8);
                String value = URLDecoder.decode(rawValue, StandardCharsets.UTF_8);
                if (parameters.putIfAbsent(key, value) != null) {
                    throw new ApiException(400, "Query parameter must not be repeated: " + key);
                }
            } catch (IllegalArgumentException exception) {
                throw new ApiException(400, "Malformed query string");
            }
        }
        return Map.copyOf(parameters);
    }

    public static int integer(Map<String, String> parameters, String name, int defaultValue) {
        String value = parameters.get(name);
        if (value == null) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException exception) {
            throw new ApiException(400, name + " must be an integer");
        }
    }

    public static long positiveLong(String value, String name) {
        try {
            long parsed = Long.parseLong(value);
            if (parsed <= 0) {
                throw new NumberFormatException("not positive");
            }
            return parsed;
        } catch (NumberFormatException exception) {
            throw new ApiException(400, name + " must be a positive integer");
        }
    }
}
