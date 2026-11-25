package com.marquee.handler;

import com.marquee.http.Json;
import com.marquee.http.Requests;
import com.marquee.http.Responses;
import com.marquee.http.Router;
import com.marquee.model.Genre;
import com.marquee.service.GenreService;
import com.sun.net.httpserver.HttpExchange;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** HTTP endpoints for genres; validation and uniqueness rules are owned by GenreService. */
public final class GenreHandler {
    private final GenreService genreService;

    public GenreHandler(GenreService genreService) {
        this.genreService = Objects.requireNonNull(genreService, "genreService");
    }

    public void registerRoutes(Router router) {
        router.add("GET", "/genres", this::listGenres);
        router.add("POST", "/genres", this::createGenre);
        router.add("GET", "/genres/{genreId}", this::getGenre);
        router.add("PUT", "/genres/{genreId}", this::updateGenre);
        router.add("DELETE", "/genres/{genreId}", this::deleteGenre);
    }

    private void listGenres(HttpExchange exchange, Map<String, String> ignored) throws IOException {
        Responses.json(exchange, 200, genreService.getAllGenres());
    }

    private void createGenre(HttpExchange exchange, Map<String, String> ignored) throws IOException {
        Genre request = Json.readBody(exchange, Genre.class);
        Responses.json(exchange, 201, genreService.createGenre(request));
    }

    private void getGenre(HttpExchange exchange, Map<String, String> path) throws IOException {
        Responses.json(exchange, 200, genreService.getGenreById(pathId(path)));
    }

    private void updateGenre(HttpExchange exchange, Map<String, String> path) throws IOException {
        Genre request = Json.readBody(exchange, Genre.class);
        Responses.json(exchange, 200, genreService.updateGenre(pathId(path), request));
    }

    private void deleteGenre(HttpExchange exchange, Map<String, String> path) throws IOException {
        long genreId = pathId(path);
        if (!genreService.deleteGenre(genreId)) {
            Responses.error(exchange, 404, "Genre " + genreId + " not found", List.of());
            return;
        }
        Responses.noContent(exchange);
    }

    private static long pathId(Map<String, String> path) {
        return Requests.positiveLong(path.get("genreId"), "genreId");
    }
}
