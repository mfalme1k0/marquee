package com.marquee.handler;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.marquee.db.DatabaseInitializer;
import com.marquee.db.DatabaseManager;
import com.marquee.db.transactions.TransactionManager;
import com.marquee.http.Router;
import com.marquee.logging.RecordingLogger;
import com.marquee.repository.FilmRepository;
import com.marquee.repository.GenreRepository;
import com.marquee.repository.ScreeningRepository;
import com.marquee.repository.SqliteFilmRepository;
import com.marquee.repository.SqliteGenreRepository;
import com.marquee.repository.SqliteScreeningRepository;
import com.marquee.service.FilmService;
import com.marquee.service.GenreService;
import com.marquee.service.ScreeningService;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Path;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ApiIntegrationTest {
    @TempDir
    Path tempDirectory;

    private HttpServer server;
    private HttpClient client;
    private URI baseUri;

    @BeforeEach
    void startApi() throws Exception {
        DatabaseManager databaseManager = new DatabaseManager(tempDirectory.resolve("api-test.db"));
        new DatabaseInitializer(databaseManager).initialize();
        TransactionManager transactionManager = new TransactionManager(databaseManager);
        FilmRepository filmRepository = new SqliteFilmRepository(databaseManager, transactionManager);
        GenreRepository genreRepository = new SqliteGenreRepository(databaseManager, transactionManager);
        ScreeningRepository screeningRepository = new SqliteScreeningRepository(databaseManager, transactionManager);

        RecordingLogger logger = new RecordingLogger();
        Router router = new Router(logger);
        new FilmHandler(new FilmService(filmRepository, genreRepository, logger)).registerRoutes(router);
        new GenreHandler(new GenreService(genreRepository, logger)).registerRoutes(router);
        new ScreeningHandler(new ScreeningService(screeningRepository, filmRepository, logger)).registerRoutes(router);

        server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext("/", router);
        server.setExecutor(Executors.newVirtualThreadPerTaskExecutor());
        server.start();
        client = HttpClient.newHttpClient();
        baseUri = URI.create("http://localhost:" + server.getAddress().getPort());
    }

    @AfterEach
    void stopApi() {
        server.stop(0);
    }

    @Test
    void listsFilmsWithDefaultPageSizeAndOmitsDescriptions() throws Exception {
        HttpResponse<String> response = send("GET", "/films", null);
        assertEquals(200, response.statusCode());
        JsonObject body = JsonParser.parseString(response.body()).getAsJsonObject();
        assertEquals(1, body.get("page").getAsInt());
        assertEquals(10, body.get("per_page").getAsInt());
        assertEquals(15, body.get("total").getAsInt());
        assertEquals(10, body.getAsJsonArray("films").size());
        assertFalse(body.getAsJsonArray("films").get(0).getAsJsonObject().has("description"));
    }

    @Test
    void createsAndReadsFilmWithServerOwnedValues() throws Exception {
        HttpResponse<String> created = send("POST", "/films",
                "{\"film_id\":999,\"title\":\"API-created film\",\"year\":2024,\"created_at\":\"client-value\",\"genres\":[{\"genre_id\":1,\"name\":\"Action\"}]}");
        assertEquals(201, created.statusCode());
        JsonObject film = JsonParser.parseString(created.body()).getAsJsonObject();
        assertTrue(film.get("film_id").getAsLong() != 999);
        assertTrue(film.has("created_at"));
        assertEquals(1, film.getAsJsonArray("genres").size());
        HttpResponse<String> readBack = send("GET", "/films/" + film.get("film_id").getAsLong(), null);
        assertEquals(200, readBack.statusCode());
        assertEquals("API-created film", JsonParser.parseString(readBack.body()).getAsJsonObject().get("title").getAsString());
    }

    @Test
    void malformedBodyAndValidationFailureUseErrorEnvelope() throws Exception {
        HttpResponse<String> malformed = send("POST", "/films", "{broken");
        assertEquals(400, malformed.statusCode());
        assertTrue(JsonParser.parseString(malformed.body()).getAsJsonObject().has("error"));

        HttpResponse<String> invalid = send("POST", "/films", "{\"title\":\"\",\"year\":0}");
        assertEquals(400, invalid.statusCode());
        JsonObject error = JsonParser.parseString(invalid.body()).getAsJsonObject().getAsJsonObject("error");
        assertEquals(400, error.get("code").getAsInt());
        assertTrue(error.has("details"));

        HttpResponse<String> wrongType = send("POST", "/films", "{\"title\":\"Typed wrong\",\"year\":\"not-a-number\"}");
        assertEquals(400, wrongType.statusCode());
    }

    @Test
    void duplicateGenreIsConflictAndMissingFilmIsNotFound() throws Exception {
        HttpResponse<String> duplicate = send("POST", "/genres", "{\"name\":\"Action\"}");
        assertEquals(409, duplicate.statusCode());
        HttpResponse<String> missingFilm = send("GET", "/films/999999", null);
        assertEquals(404, missingFilm.statusCode());
        HttpResponse<String> screeningForMissingFilm = send("POST", "/screenings",
                "{\"film_id\":999999,\"venue\":\"Nowhere\",\"screening_time\":\"2026-11-20T19:00:00Z\"}");
        assertEquals(404, screeningForMissingFilm.statusCode());
    }

    @Test
    void filmFiltersArePaginatedAndOnlyOneFilterIsAllowed() throws Exception {
        HttpResponse<String> byTitle = send("GET", "/films?title=Invisible", null);
        assertEquals(200, byTitle.statusCode());
        JsonObject titlePage = JsonParser.parseString(byTitle.body()).getAsJsonObject();
        assertEquals(1, titlePage.get("total").getAsInt());
        assertTrue(titlePage.getAsJsonArray("films").get(0).getAsJsonObject().get("title").getAsString().contains("Invisible"));

        HttpResponse<String> byYear = send("GET", "/films?year=2023", null);
        assertEquals(1, JsonParser.parseString(byYear.body()).getAsJsonObject().get("total").getAsInt());
        HttpResponse<String> byGenre = send("GET", "/films?genre=Action", null);
        assertTrue(JsonParser.parseString(byGenre.body()).getAsJsonObject().get("total").getAsInt() > 0);
        HttpResponse<String> byGenreId = send("GET", "/films?genre_id=1&page=1&per_page=1", null);
        assertEquals(1, JsonParser.parseString(byGenreId.body()).getAsJsonObject().get("films").getAsJsonArray().size());
        assertEquals(400, send("GET", "/films?title=Invisible&year=2023", null).statusCode());
    }

    @Test
    void filmGenreAssociationSupportsAssignDuplicateAndIdempotentRemoval() throws Exception {
        HttpResponse<String> genre = send("POST", "/genres", "{\"name\":\"Association test genre\"}");
        assertEquals(201, genre.statusCode());
        long genreId = JsonParser.parseString(genre.body()).getAsJsonObject().get("genre_id").getAsLong();
        String route = "/films/1/genres";
        String payload = "{\"genre_id\":" + genreId + "}";
        assertEquals(201, send("POST", route, payload).statusCode());
        assertEquals(409, send("POST", route, payload).statusCode());
        assertEquals(204, send("DELETE", route + "/" + genreId, null).statusCode());
        assertEquals(204, send("DELETE", route + "/" + genreId, null).statusCode());
        assertEquals(204, send("DELETE", "/genres/" + genreId, null).statusCode());
        assertEquals(400, send("POST", route, "{\"genre_id\":0}").statusCode());
    }

    @Test
    void screeningFiltersUsePaginatedResponsesIncludingDate() throws Exception {
        HttpResponse<String> byFilm = send("GET", "/screenings?film_id=1", null);
        assertEquals(200, byFilm.statusCode());
        JsonObject filmPage = JsonParser.parseString(byFilm.body()).getAsJsonObject();
        assertEquals(1, filmPage.get("total").getAsInt());
        assertEquals(1, filmPage.getAsJsonArray("screenings").size());

        HttpResponse<String> byVenue = send("GET", "/screenings?venue=Main%20Hall&page=1&per_page=2", null);
        assertEquals(200, byVenue.statusCode());
        JsonObject venuePage = JsonParser.parseString(byVenue.body()).getAsJsonObject();
        assertTrue(venuePage.get("total").getAsInt() > 1);
        assertEquals(2, venuePage.getAsJsonArray("screenings").size());

        HttpResponse<String> byDate = send("GET", "/screenings?date=2025-10-03", null);
        assertEquals(200, byDate.statusCode());
        JsonObject datePage = JsonParser.parseString(byDate.body()).getAsJsonObject();
        assertEquals(1, datePage.get("total").getAsInt());
        assertEquals(1, datePage.getAsJsonArray("screenings").size());
        assertEquals(400, send("GET", "/screenings?date=03-10-2025", null).statusCode());
        assertEquals(400, send("GET", "/screenings?date=2025-10-03&venue=Main%20Hall", null).statusCode());
    }

    @Test
    void updatesAndDeletesFilmAndCascadeRemovesItsScreenings() throws Exception {
        HttpResponse<String> createdFilm = send("POST", "/films",
                "{\"title\":\"Cascade film\",\"year\":2020}");
        assertEquals(201, createdFilm.statusCode());
        long filmId = JsonParser.parseString(createdFilm.body()).getAsJsonObject().get("film_id").getAsLong();

        HttpResponse<String> createdScreening = send("POST", "/screenings",
                "{\"film_id\":" + filmId + ",\"venue\":\"Cascade room\",\"screening_time\":\"2026-11-20T19:00:00Z\"}");
        assertEquals(201, createdScreening.statusCode());
        long screeningId = JsonParser.parseString(createdScreening.body()).getAsJsonObject().get("screening_id").getAsLong();

        HttpResponse<String> updatedFilm = send("PUT", "/films/" + filmId,
                "{\"title\":\"Updated cascade film\",\"year\":2021,\"description\":\"Updated\"}");
        assertEquals(200, updatedFilm.statusCode());
        assertEquals("Updated cascade film", JsonParser.parseString(updatedFilm.body()).getAsJsonObject().get("title").getAsString());

        assertEquals(204, send("DELETE", "/films/" + filmId, null).statusCode());
        assertEquals(404, send("GET", "/screenings/" + screeningId, null).statusCode());
    }

    @Test
    void genreCanBeListedUpdatedAndDeleted() throws Exception {
        HttpResponse<String> created = send("POST", "/genres", "{\"name\":\"HTTP API created genre\"}");
        assertEquals(201, created.statusCode());
        long createdGenreId = JsonParser.parseString(created.body()).getAsJsonObject().get("genre_id").getAsLong();
        assertEquals(200, send("GET", "/genres/" + createdGenreId, null).statusCode());
        assertEquals(204, send("DELETE", "/genres/" + createdGenreId, null).statusCode());

        HttpResponse<String> genres = send("GET", "/genres", null);
        assertEquals(200, genres.statusCode());
        assertTrue(JsonParser.parseString(genres.body()).getAsJsonArray().size() >= 10);

        HttpResponse<String> updated = send("PUT", "/genres/10", "{\"name\":\"Updated Fantasy\"}");
        assertEquals(200, updated.statusCode());
        assertEquals("Updated Fantasy", JsonParser.parseString(updated.body()).getAsJsonObject().get("name").getAsString());
        assertEquals(204, send("DELETE", "/genres/10", null).statusCode());
        assertEquals(404, send("GET", "/genres/10", null).statusCode());
    }

    @Test
    void screeningCanBeCreatedReadUpdatedAndDeleted() throws Exception {
        HttpResponse<String> created = send("POST", "/screenings",
                "{\"film_id\":1,\"venue\":\"New Venue\",\"screening_time\":\"2026-11-20T19:00:00Z\"}");
        assertEquals(201, created.statusCode());
        long screeningId = JsonParser.parseString(created.body()).getAsJsonObject().get("screening_id").getAsLong();
        assertEquals(200, send("GET", "/screenings/" + screeningId, null).statusCode());

        HttpResponse<String> updated = send("PUT", "/screenings/" + screeningId,
                "{\"film_id\":1,\"venue\":\"Updated Venue\",\"screening_time\":\"2026-11-21T19:00:00Z\"}");
        assertEquals(200, updated.statusCode());
        assertEquals("Updated Venue", JsonParser.parseString(updated.body()).getAsJsonObject().get("venue").getAsString());
        assertEquals(204, send("DELETE", "/screenings/" + screeningId, null).statusCode());
        assertEquals(404, send("GET", "/screenings/" + screeningId, null).statusCode());
    }

    @Test
    void wrongMethodAndUnknownRouteAreDistinctErrors() throws Exception {
        HttpResponse<String> wrongMethod = send("PATCH", "/films/1", null);
        assertEquals(405, wrongMethod.statusCode());
        assertTrue(wrongMethod.headers().firstValue("Allow").orElse("").contains("GET"));
        HttpResponse<String> unknownRoute = send("GET", "/unknown", null);
        assertEquals(404, unknownRoute.statusCode());
    }

    private HttpResponse<String> send(String method, String path, String body) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder(baseUri.resolve(path));
        if (body == null) {
            builder.method(method, HttpRequest.BodyPublishers.noBody());
        } else {
            builder.header("Content-Type", "application/json")
                    .method(method, HttpRequest.BodyPublishers.ofString(body));
        }
        return client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }
}
