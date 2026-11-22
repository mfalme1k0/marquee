package com.marquee.http;

import com.google.gson.Gson;
import com.google.gson.JsonParseException;
import com.google.gson.Strictness;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.sun.net.httpserver.HttpExchange;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;

/** Shared JSON parser/serializer so endpoint handlers emit one consistent format. */
public final class Json {
    private static final Gson GSON = new Gson();

    private Json() {}

    public static String stringify(Object value) {
        return GSON.toJson(value);
    }

    /** Reads exactly one JSON value. Empty, malformed, or trailing JSON is a 400. */
    public static <T> T readBody(HttpExchange exchange, Class<T> type) {
        try (Reader body = new InputStreamReader(exchange.getRequestBody(), StandardCharsets.UTF_8)) {
            JsonReader reader = new JsonReader(body);
            reader.setStrictness(Strictness.STRICT);
            T value = GSON.fromJson(reader, type);
            if (value == null || reader.peek() != JsonToken.END_DOCUMENT) {
                throw new ApiException(400, "Malformed JSON");
            }
            return value;
        } catch (ApiException exception) {
            throw exception;
        } catch (IOException | JsonParseException | IllegalStateException exception) {
            throw new ApiException(400, "Malformed JSON");
        }
    }
}
