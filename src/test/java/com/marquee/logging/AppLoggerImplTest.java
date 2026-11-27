package com.marquee.logging;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonIOException;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;
import com.marquee.model.LogEntry;
import com.marquee.repository.LogRepository;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class AppLoggerImplTest {

    private static final Instant NOW = Instant.parse("2026-11-26T10:15:30.123Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);
    private static final String TS = "2026-11-26T10:15:30Z";

    @TempDir
    Path tempDir;

    private Path logFile;
    private CapturingRepository repository;
    private AppLoggerImpl logger;

    @BeforeEach
    void setUp() {
        logFile = tempDir.resolve("marquee.log");
        repository = new CapturingRepository();
        logger = new AppLoggerImpl(logFile, repository, new Gson(), CLOCK);
    }

    // ---------------------------------------------------------
    // CONSTRUCTION
    // ---------------------------------------------------------

    @Test
    void rejectsNullLogFile() {
        assertThrows(NullPointerException.class,
                () -> new AppLoggerImpl(null, repository, new Gson(), CLOCK));
    }

    @Test
    void rejectsNullGson() {
        assertThrows(NullPointerException.class,
                () -> new AppLoggerImpl(logFile, repository, null, CLOCK));
    }

    @Test
    void rejectsNullClock() {
        assertThrows(NullPointerException.class,
                () -> new AppLoggerImpl(logFile, repository, new Gson(), null));
    }

    @Test
    void acceptsNullRepositoryForFileOnlyBootstrapMode() {
        assertDoesNotThrow(() -> new AppLoggerImpl(logFile, null, new Gson(), CLOCK));
    }

    @Test
    void publicConstructorAcceptsNullRepository() {
        assertDoesNotThrow(() -> new AppLoggerImpl(null));
    }

    // ---------------------------------------------------------
    // LOG FILE PATH RESOLUTION
    // ---------------------------------------------------------

    @Test
    void resolvesDefaultLogFileWhenNothingConfigured() {
        assertEquals(Path.of("marquee.log"), AppLoggerImpl.resolveLogFile(null));
    }

    @Test
    void resolvesDefaultLogFileWhenConfiguredPathIsBlank() {
        assertEquals(Path.of("marquee.log"), AppLoggerImpl.resolveLogFile("   "));
        assertEquals(Path.of("marquee.log"), AppLoggerImpl.resolveLogFile(""));
    }

    @Test
    void resolvesConfiguredLogFile() {
        assertEquals(Path.of("/var/log/marquee/app.log"),
                AppLoggerImpl.resolveLogFile("/var/log/marquee/app.log"));
    }

    @Test
    void createParentDirectoriesIgnoresMissingParent() {
        assertDoesNotThrow(() -> AppLoggerImpl.createParentDirectories(null));
    }

    @Test
    void createParentDirectoriesCreatesNestedDirectories() throws Exception {
        Path nested = tempDir.resolve("a/b/c");

        AppLoggerImpl.createParentDirectories(nested);

        assertTrue(Files.isDirectory(nested));
    }

    // ---------------------------------------------------------
    // FILE SINK
    // ---------------------------------------------------------

    @Test
    void writesOneFormattedLinePerEvent() throws Exception {
        Map<String, Object> context = new LinkedHashMap<>();
        context.put("film_id", 1);
        context.put("title", "Heat");

        logger.info(null, "film.created", context);

        assertEquals(List.of(TS + " | INFO  | film.created | film_id=1 title=Heat"), lines());
    }

    @Test
    void padsLevelsToAlignColumns() throws Exception {
        logger.debug(null, "e.debug", Map.of());
        logger.info(null, "e.info", Map.of());
        logger.warn(null, "e.warn", Map.of());
        logger.error(null, "e.error", Map.of(), null);

        List<String> lines = lines();
        assertTrue(lines.get(0).startsWith(TS + " | DEBUG | e.debug"));
        assertTrue(lines.get(1).startsWith(TS + " | INFO  | e.info"));
        assertTrue(lines.get(2).startsWith(TS + " | WARN  | e.warn"));
        assertTrue(lines.get(3).startsWith(TS + " | ERROR | e.error"));
    }

    @Test
    void appendsToExistingFileInsteadOfOverwriting() throws Exception {
        Files.writeString(logFile, "previous run" + System.lineSeparator());

        logger.info(null, "app.starting", Map.of());

        List<String> lines = lines();
        assertEquals(2, lines.size());
        assertEquals("previous run", lines.get(0));
        assertTrue(lines.get(1).contains("app.starting"));
    }

    @Test
    void createsMissingParentDirectoriesForLogFile() throws Exception {
        Path nested = tempDir.resolve("logs/2026/app.log");
        AppLoggerImpl nestedLogger = new AppLoggerImpl(nested, null, new Gson(), CLOCK);

        nestedLogger.info(null, "app.starting", Map.of());

        assertTrue(Files.exists(nested));
    }

    @Test
    void writesFileOnlyWhenNoRepositoryIsConfigured() throws Exception {
        AppLoggerImpl bootstrap = new AppLoggerImpl(logFile, null, new Gson(), CLOCK);

        bootstrap.info(null, "app.starting", Map.of());

        assertEquals(1, lines().size());
        assertTrue(repository.entries.isEmpty());
    }

    @Test
    void acceptsNullContext() throws Exception {
        assertDoesNotThrow(() -> logger.info(null, "app.starting", null));

        assertTrue(lines().get(0).contains("| app.starting |"));
        assertEquals("{}", repository.entries.get(0).contextJson());
    }

    @Test
    void preservesContextOrderInFileLine() throws Exception {
        Map<String, Object> context = new LinkedHashMap<>();
        context.put("zeta", 1);
        context.put("alpha", 2);
        context.put("mid", 3);

        logger.info(null, "order.check", context);

        assertTrue(lines().get(0).endsWith("zeta=1 alpha=2 mid=3"));
    }

    @Test
    void writesNonAsciiTextAsUtf8() throws Exception {
        logger.info(null, "film.created", Map.of("title", "Amélie – 長い夜"));

        assertTrue(Files.readString(logFile).contains("title=Amélie – 長い夜"));
    }

    // ---------------------------------------------------------
    // DATABASE SINK
    // ---------------------------------------------------------

    @Test
    void sendsEntryToRepositoryWithLevelEventAndTimestamp() {
        logger.warn(null, "film.not_found", Map.of("film_id", 42));

        assertEquals(1, repository.entries.size());
        LogEntry entry = repository.entries.get(0);
        assertNull(entry.id());
        assertEquals("WARN", entry.level());
        assertEquals("film.not_found", entry.event());
        assertEquals(LocalDateTime.of(2026, 11, 26, 10, 15, 30, 123_000_000), entry.occurredAt());
        assertNull(entry.userId());
    }

    @Test
    void sendsContextAsJsonToRepository() {
        Map<String, Object> context = new LinkedHashMap<>();
        context.put("film_id", 42);
        context.put("title", "Heat");

        logger.info(null, "film.created", context);

        JsonObject json = JsonParser.parseString(repository.entries.get(0).contextJson()).getAsJsonObject();
        assertEquals(42, json.get("film_id").getAsInt());
        assertEquals("Heat", json.get("title").getAsString());
    }

    @Test
    void writesToFileAndDatabaseForEveryLevel() throws Exception {
        logger.debug(null, "e.debug", Map.of());
        logger.info(null, "e.info", Map.of());
        logger.warn(null, "e.warn", Map.of());
        logger.error(null, "e.error", Map.of(), new RuntimeException("x"));

        assertEquals(4, lines().size());
        assertEquals(List.of("DEBUG", "INFO", "WARN", "ERROR"),
                repository.entries.stream().map(LogEntry::level).toList());
    }

    // ---------------------------------------------------------
    // USER AND ERROR CONTEXT
    // ---------------------------------------------------------

    @Test
    void addsUserIdToContextAndEntry() throws Exception {
        logger.info(7L, "film.created", Map.of("film_id", 1));

        LogEntry entry = repository.entries.get(0);
        assertEquals(7L, entry.userId());
        assertEquals(7, JsonParser.parseString(entry.contextJson()).getAsJsonObject().get("user_id").getAsInt());
        assertTrue(lines().get(0).contains("user_id=7"));
    }

    @Test
    void ignoresCallerSuppliedUserIdWhenNoUserIsAuthenticated() throws Exception {
        logger.info(null, "film.created", Map.of("user_id", 999));

        assertFalse(repository.entries.get(0).contextJson().contains("user_id"));
        assertFalse(lines().get(0).contains("user_id"));
    }

    @Test
    void explicitUserIdOverridesUserIdInContext() {
        logger.info(7L, "film.created", Map.of("user_id", 999));

        JsonObject json = JsonParser.parseString(repository.entries.get(0).contextJson()).getAsJsonObject();
        assertEquals(7, json.get("user_id").getAsInt());
    }

    @Test
    void errorRecordsExceptionTypeButNotItsMessage() throws Exception {
        logger.error(null, "db.init.failed", Map.of("db", "sqlite"),
                new IllegalStateException("jdbc:sqlite:/secret/path failed"));

        String contextJson = repository.entries.get(0).contextJson();
        assertEquals("IllegalStateException",
                JsonParser.parseString(contextJson).getAsJsonObject().get("error_type").getAsString());
        assertFalse(contextJson.contains("secret"));
        assertTrue(lines().get(0).contains("error_type=IllegalStateException"));
        assertFalse(lines().get(0).contains("secret"));
    }

    @Test
    void errorWithoutCauseOmitsErrorType() {
        logger.error(null, "db.init.failed", Map.of(), null);

        assertFalse(repository.entries.get(0).contextJson().contains("error_type"));
    }

    // ---------------------------------------------------------
    // REDACTION
    // ---------------------------------------------------------

    @Test
    void redactsSensitiveFieldsInFileAndDatabase() throws Exception {
        Map<String, Object> context = new LinkedHashMap<>();
        context.put("password", "hunter2");
        context.put("password_hash", "$2a$abc");
        context.put("passwordHash", "$2a$def");
        context.put("current_password", "old-secret");
        context.put("new_password", "new-secret");
        context.put("username", "fidel");

        logger.info(null, "user.login", context);

        String json = repository.entries.get(0).contextJson();
        String line = lines().get(0);
        for (String secret : List.of("hunter2", "$2a$abc", "$2a$def", "old-secret", "new-secret")) {
            assertFalse(json.contains(secret), "DB leaked " + secret);
            assertFalse(line.contains(secret), "file leaked " + secret);
        }
        assertTrue(json.contains("[REDACTED]"));
        assertTrue(line.contains("password=[REDACTED]"));
        assertTrue(line.contains("username=fidel"));
    }

    @Test
    void doesNotMutateCallersContextMap() {
        Map<String, Object> context = new LinkedHashMap<>();
        context.put("password", "hunter2");

        logger.info(7L, "user.login", context);

        assertEquals(Map.of("password", "hunter2"), context);
    }

    // ---------------------------------------------------------
    // LOG-FORGING PROTECTION
    // ---------------------------------------------------------

    @Test
    void escapesLineBreaksSeparatorsAndBackslashesInValues() throws Exception {
        logger.info(null, "film.created", Map.of("title", "a\nb\rc\td|e\\f"));

        List<String> lines = lines();
        assertEquals(1, lines.size());
        assertTrue(lines.get(0).endsWith("title=a\\nb\\rc\\td\\|e\\\\f"), lines.get(0));
    }

    @Test
    void injectedFakeLogLineStaysOnOneLine() throws Exception {
        String forged = "x\n" + TS + " | ERROR | admin.login | user=root";

        logger.info(null, "film.created", Map.of("title", forged));

        assertEquals(1, lines().size());
    }

    @Test
    void keepsRawValueInDatabaseJsonRatherThanFileEscaping() {
        logger.info(null, "film.created", Map.of("title", "a|b"));

        assertEquals("a|b", JsonParser.parseString(repository.entries.get(0).contextJson())
                .getAsJsonObject().get("title").getAsString());
    }

    // ---------------------------------------------------------
    // INVALID EVENT NAMES
    // ---------------------------------------------------------

    @Test
    void reportsFailureInsteadOfLoggingNullEventName() throws Exception {
        assertDoesNotThrow(() -> logger.info(null, null, Map.of("a", 1)));

        assertEquals(1, lines().size());
        assertTrue(lines().get(0).contains("| ERROR | logging.failure | message=Cannot write log event with a missing event name"));
        assertEquals(List.of("logging.failure"), repository.entries.stream().map(LogEntry::event).toList());
        assertEquals("ERROR", repository.entries.get(0).level());
    }

    @Test
    void reportsFailureInsteadOfLoggingBlankEventName() throws Exception {
        logger.warn(null, "   ", Map.of());

        assertEquals(1, lines().size());
        assertTrue(lines().get(0).contains("logging.failure"));
        assertEquals(1, repository.entries.size());
    }

    // ---------------------------------------------------------
    // SINK FAILURES MUST NEVER BREAK THE CALLER
    // ---------------------------------------------------------

    @Test
    void databaseFailureDoesNotPropagateAndIsRecordedInFile() throws Exception {
        repository.failWith = new IllegalStateException("db locked");

        assertDoesNotThrow(() -> logger.info(null, "film.created", Map.of("film_id", 1)));

        List<String> lines = lines();
        assertEquals(2, lines.size());
        assertTrue(lines.get(0).contains("| film.created |"));
        assertTrue(lines.get(1).contains("logging.failure"));
        assertTrue(lines.get(1).contains("Could not persist log event: film.created (IllegalStateException)"));
    }

    @Test
    void databaseFailureNeverLeaksExceptionMessageIntoFailureLine() throws Exception {
        repository.failWith = new IllegalStateException("password=hunter2");

        logger.info(null, "film.created", Map.of());

        assertFalse(Files.readString(logFile).contains("hunter2"));
    }

    @Test
    void fileFailureDoesNotPropagateAndDatabaseStillReceivesEvent() throws Exception {
        // A directory cannot be opened for appending, so every file write fails.
        AppLoggerImpl broken = new AppLoggerImpl(tempDir, repository, new Gson(), CLOCK);

        assertDoesNotThrow(() -> broken.info(null, "film.created", Map.of("film_id", 1)));

        assertTrue(repository.entries.stream().anyMatch(e -> e.event().equals("film.created")));
    }

    @Test
    void bothSinksFailingStillDoesNotThrow() {
        repository.failWith = new IllegalStateException("db locked");
        AppLoggerImpl broken = new AppLoggerImpl(tempDir, repository, new Gson(), CLOCK);

        assertDoesNotThrow(() -> broken.error(null, "app.crash", Map.of(), new RuntimeException()));
    }

    @Test
    void unserialisableContextFallsBackToMarkerJsonAndReportsFailure() throws Exception {
        Gson failing = new GsonBuilder()
                .registerTypeAdapter(Unserialisable.class, new TypeAdapter<Unserialisable>() {
                    @Override
                    public void write(JsonWriter out, Unserialisable value) {
                        throw new JsonIOException("cannot serialise");
                    }

                    @Override
                    public Unserialisable read(JsonReader in) throws IOException {
                        throw new UnsupportedOperationException();
                    }
                })
                .create();
        AppLoggerImpl impl = new AppLoggerImpl(logFile, repository, failing, CLOCK);

        assertDoesNotThrow(() -> impl.info(null, "film.created", Map.of("bad", new Unserialisable())));

        LogEntry entry = repository.entries.stream()
                .filter(e -> e.event().equals("film.created"))
                .findFirst()
                .orElseThrow();
        assertEquals("{\"context_serialization_error\":true}", entry.contextJson());
        String file = Files.readString(logFile);
        assertTrue(file.contains("| film.created |"));
        assertTrue(file.contains("Could not serialize log context"));
    }

    // ---------------------------------------------------------
    // CONCURRENCY (the HTTP server runs handlers on virtual threads)
    // ---------------------------------------------------------

    @Test
    void concurrentLoggingProducesIntactLines() throws Exception {
        int threads = 8;
        int perThread = 50;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        try {
            for (int t = 0; t < threads; t++) {
                int id = t;
                pool.submit(() -> {
                    start.await();
                    for (int i = 0; i < perThread; i++) {
                        logger.info(null, "load.test", Map.of("thread", id, "n", i));
                    }
                    return null;
                });
            }
            start.countDown();
        } finally {
            pool.shutdown();
            assertTrue(pool.awaitTermination(30, TimeUnit.SECONDS));
        }

        List<String> lines = lines();
        assertEquals(threads * perThread, lines.size());
        for (String line : lines) {
            assertTrue(line.matches("\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}Z \\| INFO  \\| load\\.test \\| (thread=\\d n=\\d+|n=\\d+ thread=\\d)"),
                    "Corrupted line: " + line);
        }
        assertEquals(threads * perThread, repository.entries.size());
    }

    // ---------------------------------------------------------
    // HELPERS
    // ---------------------------------------------------------

    private List<String> lines() throws IOException {
        assertNotNull(logFile);
        return Files.readAllLines(logFile);
    }

    private static final class Unserialisable {}

    private static final class CapturingRepository implements LogRepository {
        final List<LogEntry> entries = Collections.synchronizedList(new ArrayList<>());
        volatile RuntimeException failWith;

        @Override
        public void append(LogEntry entry) {
            if (failWith != null) {
                throw failWith;
            }
            entries.add(entry);
        }
    }
}
