package com.marquee.handler;

import com.marquee.http.ApiException;
import com.marquee.http.Json;
import com.marquee.http.Requests;
import com.marquee.http.Responses;
import com.marquee.http.Router;
import com.marquee.model.Page;
import com.marquee.model.Screening;
import com.marquee.service.ScreeningService;
import com.sun.net.httpserver.HttpExchange;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** HTTP endpoints for screenings; query execution and validation stay in ScreeningService. */
public final class ScreeningHandler {
    private static final int DEFAULT_PAGE = 1;
    private static final int DEFAULT_PER_PAGE = 10;

    private final ScreeningService screeningService;

    public ScreeningHandler(ScreeningService screeningService) {
        this.screeningService = Objects.requireNonNull(screeningService, "screeningService");
    }

    public void registerRoutes(Router router) {
        router.add("GET", "/screenings", this::listScreenings);
        router.add("POST", "/screenings", this::createScreening);
        router.add("GET", "/screenings/{screeningId}", this::getScreening);
        router.add("PUT", "/screenings/{screeningId}", this::updateScreening);
        router.add("DELETE", "/screenings/{screeningId}", this::deleteScreening);
    }

    private void listScreenings(HttpExchange exchange, Map<String, String> ignored) throws IOException {
        Map<String, String> query = Requests.queryParameters(exchange);
        rejectUnknownParameters(query, List.of("page", "per_page", "film_id", "venue", "date"));
        int page = Requests.integer(query, "page", DEFAULT_PAGE);
        int perPage = Requests.integer(query, "per_page", DEFAULT_PER_PAGE);
        int filters = countPresent(query, "film_id", "venue", "date");
        if (filters > 1) {
            throw new ApiException(400, "Use only one screening filter at a time");
        }

        Page<Screening> result;
        if (query.containsKey("film_id")) {
            long filmId = Requests.positiveLong(query.get("film_id"), "film_id");
            result = screeningService.getScreeningsByFilmId(filmId, page, perPage);
        } else if (query.containsKey("venue")) {
            result = screeningService.getScreeningsByVenue(query.get("venue"), page, perPage);
        } else if (query.containsKey("date")) {
            result = screeningService.getScreeningsByDate(query.get("date"), page, perPage);
        } else {
            result = screeningService.getAllScreenings(page, perPage);
        }
        Responses.json(exchange, 200, result.toResponse("screenings"));
    }

    private void createScreening(HttpExchange exchange, Map<String, String> ignored) throws IOException {
        Responses.json(exchange, 201, screeningService.createScreening(Json.readBody(exchange, Screening.class)));
    }

    private void getScreening(HttpExchange exchange, Map<String, String> path) throws IOException {
        Responses.json(exchange, 200, screeningService.getScreeningById(pathId(path)));
    }

    private void updateScreening(HttpExchange exchange, Map<String, String> path) throws IOException {
        long screeningId = pathId(path);
        Screening request = Json.readBody(exchange, Screening.class);
        Responses.json(exchange, 200, screeningService.updateScreening(screeningId, request));
    }

    private void deleteScreening(HttpExchange exchange, Map<String, String> path) throws IOException {
        long screeningId = pathId(path);
        if (!screeningService.deleteScreening(screeningId)) {
            Responses.error(exchange, 404, "Screening " + screeningId + " not found", List.of());
            return;
        }
        Responses.noContent(exchange);
    }

    private static int countPresent(Map<String, String> query, String... names) {
        int count = 0;
        for (String name : names) {
            if (query.containsKey(name)) {
                count++;
            }
        }
        return count;
    }

    private static long pathId(Map<String, String> path) {
        return Requests.positiveLong(path.get("screeningId"), "screeningId");
    }

    private static void rejectUnknownParameters(Map<String, String> query, List<String> accepted) {
        query.keySet().stream().filter(name -> !accepted.contains(name)).findFirst().ifPresent(name -> {
            throw new ApiException(400, "Unsupported query parameter: " + name);
        });
    }
}
