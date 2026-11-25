package com.marquee.handler;

import com.marquee.http.ApiException;
import com.marquee.http.Json;
import com.marquee.http.Requests;
import com.marquee.http.Responses;
import com.marquee.http.Router;
import com.marquee.model.Film;
import com.marquee.model.Page;
import com.marquee.service.FilmService;
import com.sun.net.httpserver.HttpExchange;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** HTTP endpoints for films; validation and decisions are delegated to FilmService. */
public final class FilmHandler {
    private static final int DEFAULT_PAGE = 1;
    private static final int DEFAULT_PER_PAGE = 10;

    private final FilmService filmService;

    public FilmHandler(FilmService filmService) {
        this.filmService = Objects.requireNonNull(filmService, "filmService");
    }

    public void registerRoutes(Router router) {
        router.add("GET", "/films", this::listFilms);
        router.add("POST", "/films", this::createFilm);
        router.add("GET", "/films/{filmId}", this::getFilm);
        router.add("PUT", "/films/{filmId}", this::updateFilm);
        router.add("DELETE", "/films/{filmId}", this::deleteFilm);
        router.add("POST", "/films/{filmId}/genres", this::assignGenre);
        router.add("DELETE", "/films/{filmId}/genres/{genreId}", this::removeGenre);
    }

    private void listFilms(HttpExchange exchange, Map<String, String> ignored) throws IOException {
        Map<String, String> query = Requests.queryParameters(exchange);
        rejectUnknownParameters(query, List.of("page", "per_page", "genre", "genre_id", "year", "title"));
        int page = Requests.integer(query, "page", DEFAULT_PAGE);
        int perPage = Requests.integer(query, "per_page", DEFAULT_PER_PAGE);
        int filters = countPresent(query, "genre", "genre_id", "year", "title");
        if (filters > 1) {
            throw new ApiException(400, "Use only one film filter at a time");
        }

        Page<Film> result;
        if (query.containsKey("title")) {
            result = filmService.getFilmsByTitle(query.get("title"), page, perPage);
        } else if (query.containsKey("year")) {
            result = filmService.getFilmsByYear(Requests.integer(query, "year", 0), page, perPage);
        } else if (query.containsKey("genre_id")) {
            result = filmService.getFilmsByGenreId(
                    Requests.positiveLong(query.get("genre_id"), "genre_id"), page, perPage);
        } else if (query.containsKey("genre")) {
            result = filmService.getFilmsByGenreName(query.get("genre"), page, perPage);
        } else {
            result = filmService.getAllFilms(page, perPage);
        }

        List<Film> listItems = result.items().stream().map(Film::withoutDescription).toList();
        Page<Film> listView = new Page<>(result.page(), result.perPage(), result.total(), listItems);
        Responses.json(exchange, 200, listView.toResponse("films"));
    }

    private void createFilm(HttpExchange exchange, Map<String, String> ignored) throws IOException {
        Responses.json(exchange, 201, filmService.createFilm(Json.readBody(exchange, Film.class)));
    }

    private void getFilm(HttpExchange exchange, Map<String, String> path) throws IOException {
        Responses.json(exchange, 200, filmService.getFilmById(pathId(path, "filmId")));
    }

    private void updateFilm(HttpExchange exchange, Map<String, String> path) throws IOException {
        long filmId = pathId(path, "filmId");
        Film request = Json.readBody(exchange, Film.class);
        Responses.json(exchange, 200, filmService.updateFilm(filmId, request));
    }

    private void deleteFilm(HttpExchange exchange, Map<String, String> path) throws IOException {
        long filmId = pathId(path, "filmId");
        if (!filmService.deleteFilm(filmId)) {
            Responses.error(exchange, 404, "Film " + filmId + " not found", List.of());
            return;
        }
        Responses.noContent(exchange);
    }

    private void assignGenre(HttpExchange exchange, Map<String, String> path) throws IOException {
        long filmId = pathId(path, "filmId");
        GenreAssignmentRequest request = Json.readBody(exchange, GenreAssignmentRequest.class);
        if (request.genreId() == null || request.genreId() <= 0) {
            throw new ApiException(400, "genre_id must be a positive integer");
        }
        long genreId = request.genreId();
        if (!filmService.assignGenreToFilm(filmId, genreId)) {
            Responses.error(exchange, 409, "Genre is already assigned to this film", List.of());
            return;
        }
        Responses.json(exchange, 201, Map.of("film_id", filmId, "genre_id", genreId));
    }

    private void removeGenre(HttpExchange exchange, Map<String, String> path) throws IOException {
        long filmId = pathId(path, "filmId");
        long genreId = pathId(path, "genreId");
        // DELETE is idempotent: an absent association is already in the requested state.
        filmService.removeGenreFromFilm(filmId, genreId);
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

    private static long pathId(Map<String, String> path, String name) {
        return Requests.positiveLong(path.get(name), name);
    }

    private static void rejectUnknownParameters(Map<String, String> query, List<String> accepted) {
        query.keySet().stream().filter(name -> !accepted.contains(name)).findFirst().ifPresent(name -> {
            throw new ApiException(400, "Unsupported query parameter: " + name);
        });
    }
}
