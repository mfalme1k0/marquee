package com.marquee;

import com.marquee.db.DatabaseInitializer;
import com.marquee.db.DatabaseManager;
import com.marquee.db.transactions.TransactionManager;
import com.marquee.handler.FilmHandler;
import com.marquee.handler.GenreHandler;
import com.marquee.handler.ScreeningHandler;
import com.marquee.http.Router;
import com.marquee.logging.AppLogger;
import com.marquee.logging.AppLoggerImpl;
import com.marquee.repository.FilmRepository;
import com.marquee.repository.GenreRepository;
import com.marquee.repository.LogRepository;
import com.marquee.repository.ScreeningRepository;
import com.marquee.repository.SqliteFilmRepository;
import com.marquee.repository.SqliteGenreRepository;
import com.marquee.repository.SqliteLogRepository;
import com.marquee.repository.SqliteScreeningRepository;
import com.marquee.service.FilmService;
import com.marquee.service.GenreService;
import com.marquee.service.ScreeningService;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.sql.SQLException;
import java.util.Map;
import java.util.concurrent.Executors;

/** Application composition root: constructs dependencies and starts the HTTP server. */
public final class Main {

    // Phase-0 logger: file-only, available before any other object exists.
    // This is the ONLY logger that can capture a failure from
    // DatabaseInitializer.initialize(), because the log_events table does
    // not exist until initialize() returns successfully.
    private static final AppLogger bootstrapLog = new AppLoggerImpl(null);

    private Main() {}

    public static void main(String[] args) throws IOException, SQLException {

        // 1. Logging is live before any other code runs.
        bootstrapLog.info(null, "app.starting", Map.of());

        // 2. Database initialisation.
        //    Failures are captured by the FILE sink only — the DB cannot log
        //    its own failure. That is the entire point of the bootstrap logger.
        DatabaseManager databaseManager = new DatabaseManager();
        try {
            new DatabaseInitializer(databaseManager).initialize();
            bootstrapLog.info(null, "db.init.succeeded", Map.of("db", "sqlite"));
        } catch (SQLException | IOException e) {
            bootstrapLog.error(null, "db.init.failed", Map.of("db", "sqlite"), e);
            throw e;   // re-throw so the process exits with a non-zero status
        }

        // 3. DB is up → swap to the dual-sink logger (file + DB).
        //    From here on, every event is also queryable via SQL.
        TransactionManager transactionManager = new TransactionManager(databaseManager);
        LogRepository logRepository = new SqliteLogRepository(databaseManager, transactionManager);
        AppLogger log = new AppLoggerImpl(logRepository);

        // 4. Repositories.
        FilmRepository filmRepository = new SqliteFilmRepository(databaseManager, transactionManager);
        GenreRepository genreRepository = new SqliteGenreRepository(databaseManager, transactionManager);
        ScreeningRepository screeningRepository = new SqliteScreeningRepository(databaseManager, transactionManager);

        // 5. Services receive the dual-sink logger via constructor.
        FilmService filmService = new FilmService(filmRepository, genreRepository, log);
        GenreService genreService = new GenreService(genreRepository, log);
        ScreeningService screeningService = new ScreeningService(screeningRepository, filmRepository, log);

        // 6. HTTP layer — Router receives the logger for the catch-all.
        Router router = new Router(log);
        new FilmHandler(filmService).registerRoutes(router);
        new GenreHandler(genreService).registerRoutes(router);
        new ScreeningHandler(screeningService).registerRoutes(router);

        int port = Integer.parseInt(System.getenv().getOrDefault("PORT", "8080"));
        HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);
        server.createContext("/", router);
        server.setExecutor(Executors.newVirtualThreadPerTaskExecutor());
        Runtime.getRuntime().addShutdownHook(new Thread(() -> server.stop(0), "marquee-shutdown"));
        server.start();

        log.info(null, "app.listening", Map.of("port", port));
    }
}