package com.marquee;

import com.marquee.db.DatabaseInitializer;
import com.marquee.db.DatabaseManager;
import com.marquee.db.transactions.TransactionManager;
import com.marquee.handler.FilmHandler;
import com.marquee.handler.GenreHandler;
import com.marquee.handler.ScreeningHandler;
import com.marquee.http.Router;
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
import java.io.IOException;
import java.net.InetSocketAddress;
import java.sql.SQLException;
import java.util.concurrent.Executors;

/** Application composition root: constructs dependencies and starts the HTTP server. */
public final class Main {
    private Main() {}

    public static void main(String[] args) throws IOException, SQLException {
        DatabaseManager databaseManager = new DatabaseManager();
        new DatabaseInitializer(databaseManager).initialize();

        TransactionManager transactionManager = new TransactionManager(databaseManager);
        FilmRepository filmRepository = new SqliteFilmRepository(databaseManager, transactionManager);
        GenreRepository genreRepository = new SqliteGenreRepository(databaseManager, transactionManager);
        ScreeningRepository screeningRepository = new SqliteScreeningRepository(databaseManager, transactionManager);

        FilmService filmService = new FilmService(filmRepository, genreRepository);
        GenreService genreService = new GenreService(genreRepository);
        ScreeningService screeningService = new ScreeningService(screeningRepository, filmRepository);

        Router router = new Router();
        new FilmHandler(filmService).registerRoutes(router);
        new GenreHandler(genreService).registerRoutes(router);
        new ScreeningHandler(screeningService).registerRoutes(router);

        int port = Integer.parseInt(System.getenv().getOrDefault("PORT", "8080"));
        HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);
        server.createContext("/", router);
        server.setExecutor(Executors.newVirtualThreadPerTaskExecutor());
        Runtime.getRuntime().addShutdownHook(new Thread(() -> server.stop(0), "marquee-shutdown"));
        server.start();
        System.out.println("Marquee listening on :" + port);
    }
}
