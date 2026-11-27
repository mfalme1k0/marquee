package com.marquee.handler;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.marquee.db.DatabaseInitializer;
import com.marquee.db.DatabaseManager;
import com.marquee.db.transactions.TransactionManager;
import com.marquee.http.Router;
import com.marquee.logging.AppLoggerImpl;
import com.marquee.repository.FilmRepository;
import com.marquee.repository.GenreRepository;
import com.marquee.repository.ScreeningRepository;
import com.marquee.repository.SqliteFilmRepository;
import com.marquee.repository.SqliteGenreRepository;
import com.marquee.repository.SqliteLogRepository;
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
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Wires the real stack the same way {@code Main} does (HTTP -> services -> real
 * {@link AppLoggerImpl} -> SQLite + log file) and checks that requests leave the
 * expected audit trail.
 */
class LoggingIntegrationTest {

    @TempDir
    Path tempDirectory;

    private DatabaseManager databaseManager;
    private Path logFile;
    private HttpServer server;
    private HttpClient client;
    private URI baseUri;

    @BeforeEach
    void startApi() throws Exception {
        databaseManager = new DatabaseManager(tempDirectory.resolve("logging-test.db"));
        new DatabaseInitializer(databaseManager).initialize();
        TransactionManager tx = new TransactionManager(databaseManager);

        logFile = tempDirectory.resolve("marquee.log");
        AppLoggerImpl log = new AppLoggerImpl(
                logFile, new SqliteLogRepository(databaseManager, tx), new Gson(), Clock.systemUTC());

        FilmRepository filmRepository = new SqliteFilmRepository(databaseManager, tx);
        GenreRepository genreRepository = new SqliteGenreRepository(databaseManager, tx);
        ScreeningRepository screeningRepository = new SqliteScreeningRepository(databaseManager, tx);

        Router router = new Router(log);
        new FilmHandler(new FilmService(filmRepository, genreRepository, log)).registerRoutes(router);
        new GenreHandler(new GenreService(genreRepository, log)).registerRoutes(router);
        new ScreeningHandler(new ScreeningService(screeningRepository, filmRepository, log)).registerRoutes(router);

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
    void successfulReadsWriteNoLogEvents() throws Exception {
        assertEquals(200, send("GET", "/films", null).statusCode());
        assertEquals(200, send("GET", "/genres", null).statusCode());

        assertEquals(0, logRows().size());
        assertFalse(Files.exists(logFile));
    }

    @Test
    void creatingFilmIsLoggedToDatabaseAndFile() throws Exception {
        HttpResponse<String> created = send("POST", "/films", "{\"title\":\"Logged film\",\"year\":2024}");
        assertEquals(201, created.statusCode());
        long filmId = JsonParser.parseString(created.body()).getAsJsonObject().get("film_id").getAsLong();

        List<LogRow> rows = logRows();
        assertEquals(1, rows.size());
        LogRow row = rows.get(0);
        assertEquals("film.created", row.event());
        assertEquals("INFO", row.level());
        JsonObject context = JsonParser.parseString(row.contextJson()).getAsJsonObject();
        assertEquals(filmId, context.get("film_id").getAsLong());
        assertEquals("Logged film", context.get("title").getAsString());

        List<String> fileLines = Files.readAllLines(logFile);
        assertEquals(1, fileLines.size());
        assertTrue(fileLines.get(0).contains("| INFO  | film.created |"));
        assertTrue(fileLines.get(0).contains("film_id=" + filmId));
    }

    @Test
    void invalidFilmIsLoggedAsRejectedWarning() throws Exception {
        assertEquals(400, send("POST", "/films", "{\"title\":\"\",\"year\":0}").statusCode());

        List<LogRow> rows = logRows();
        assertEquals(1, rows.size());
        assertEquals("film.create.rejected", rows.get(0).event());
        assertEquals("WARN", rows.get(0).level());
        assertEquals("ValidationException",
                JsonParser.parseString(rows.get(0).contextJson()).getAsJsonObject().get("reason").getAsString());
    }

    @Test
    void missingFilmIsLoggedAsNotFoundWarning() throws Exception {
        assertEquals(404, send("GET", "/films/999999", null).statusCode());

        List<LogRow> rows = logRows();
        assertEquals(1, rows.size());
        assertEquals("film.not_found", rows.get(0).event());
        assertEquals(999999L,
                JsonParser.parseString(rows.get(0).contextJson()).getAsJsonObject().get("film_id").getAsLong());
    }

    @Test
    void duplicateGenreDoesNotLogACreation() throws Exception {
        assertEquals(409, send("POST", "/genres", "{\"name\":\"Action\"}").statusCode());

        assertFalse(logRows().stream().anyMatch(r -> r.event().equals("genre.created")));
    }

    @Test
    void screeningLifecycleIsLoggedInOrder() throws Exception {
        HttpResponse<String> created = send("POST", "/screenings",
                "{\"film_id\":1,\"venue\":\"Cinema One\",\"screening_time\":\"2026-11-20T19:00:00Z\"}");
        assertEquals(201, created.statusCode());
        long screeningId = JsonParser.parseString(created.body()).getAsJsonObject().get("screening_id").getAsLong();
        assertEquals(204, send("DELETE", "/screenings/" + screeningId, null).statusCode());
        assertEquals(404, send("POST", "/screenings",
                "{\"film_id\":999999,\"venue\":\"Nowhere\",\"screening_time\":\"2026-11-20T19:00:00Z\"}").statusCode());

        assertEquals(
                List.of("screening.created", "screening.deleted", "screening.create.rejected"),
                logRows().stream().map(LogRow::event).toList());
    }

    @Test
    void everyLogRowHasValidTimestampAndJsonContext() throws Exception {
        send("POST", "/films", "{\"title\":\"One\",\"year\":2020}");
        send("GET", "/films/999999", null);
        send("POST", "/films", "{\"title\":\"\",\"year\":0}");

        List<LogRow> rows = logRows();
        assertEquals(3, rows.size());
        for (LogRow row : rows) {
            LocalDateTime.parse(row.occurredAt());
            assertTrue(JsonParser.parseString(row.contextJson()).isJsonObject());
        }
    }

    private List<LogRow> logRows() throws Exception {
        List<LogRow> rows = new ArrayList<>();
        try (Connection connection = databaseManager.getConnection();
             Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery(
                     "SELECT occurred_at, level, event, context_json FROM log_events ORDER BY id")) {
            while (rs.next()) {
                rows.add(new LogRow(
                        rs.getString("occurred_at"),
                        rs.getString("level"),
                        rs.getString("event"),
                        rs.getString("context_json")));
            }
        }
        return rows;
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

    private record LogRow(String occurredAt, String level, String event, String contextJson) {}
}
