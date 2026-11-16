package com.marquee.repository;

import com.marquee.db.DatabaseInitializer;
import com.marquee.db.DatabaseManager;
import com.marquee.db.transactions.ConnectionProvider;
import com.marquee.db.transactions.TransactionManager;
import com.marquee.exceptions.FilmNotFoundException;
import com.marquee.exceptions.RepositoryException;
import com.marquee.exceptions.ScreeningNotFoundException;
import com.marquee.model.Film;
import com.marquee.model.Screening;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class SqliteScreeningRepositoryTest {

    @TempDir
    Path tempDir;

    private DatabaseManager databaseManager;
    private ScreeningRepository screeningRepository;
    private FilmRepository filmRepository;

    @BeforeEach
    void setUp() throws Exception {
        databaseManager = new DatabaseManager(
                tempDir.resolve("marquee.db")
        );

        DatabaseInitializer databaseInitializer =
                new DatabaseInitializer(databaseManager);

        databaseInitializer.initialize();

        // Remove seed records so every test starts with empty tables.
        try (Connection connection = databaseManager.getConnection();
             Statement statement = connection.createStatement()) {

            statement.executeUpdate("DELETE FROM screenings");
            statement.executeUpdate("DELETE FROM films");
            statement.executeUpdate("DELETE FROM genres");
        }

        ConnectionProvider connectionProvider = databaseManager;

        TransactionManager transactionManager =
                new TransactionManager(connectionProvider);

        screeningRepository = new SqliteScreeningRepository(
                connectionProvider,
                transactionManager
        );

        filmRepository = new SqliteFilmRepository(
                connectionProvider,
                transactionManager
        );
    }

    @Test
    void shouldCreateScreeningAndReturnGeneratedIdAndFilmDetails() {
        Film film = createFilm("Test Film");

        Screening screening = screeningRepository.createScreening(
                new Screening(
                        null,
                        film.filmId(),
                        null,
                        "Test Cinema",
                        "2026-12-01T18:00:00"
                )
        );

        assertNotNull(screening.screeningId());
        assertTrue(screening.screeningId() > 0);
        assertEquals(film.filmId(), screening.filmId());
        assertEquals("Test Film", screening.filmTitle());
        assertEquals("Test Cinema", screening.venue());
        assertEquals("2026-12-01T18:00:00", screening.screeningTime());
    }

    @Test
    void shouldFindScreeningById() {
        Screening createdScreening = createScreening(
                "Test Film",
                "Test Cinema",
                "2026-12-01T18:00:00"
        );

        Optional<Screening> result =
                screeningRepository.findScreeningById(
                        createdScreening.screeningId()
                );

        assertTrue(result.isPresent());
        assertEquals(createdScreening, result.get());
    }

    @Test
    void shouldReturnEmptyWhenScreeningIdDoesNotExist() {
        Optional<Screening> result =
                screeningRepository.findScreeningById(-1L);

        assertTrue(result.isEmpty());
    }

    @Test
    void shouldFindAllScreeningsOrderedByScreeningTime() {
        createScreening(
                "Film One",
                "Cinema One",
                "2026-12-03T18:00:00"
        );

        createScreening(
                "Film Two",
                "Cinema Two",
                "2026-12-01T18:00:00"
        );

        createScreening(
                "Film Three",
                "Cinema Three",
                "2026-12-02T18:00:00"
        );

        List<Screening> screenings =
                screeningRepository.findAllScreenings();

        assertEquals(3, screenings.size());

        assertEquals(
                List.of(
                        "2026-12-01T18:00:00",
                        "2026-12-02T18:00:00",
                        "2026-12-03T18:00:00"
                ),
                screenings.stream()
                        .map(Screening::screeningTime)
                        .toList()
        );
    }

    @Test
    void shouldOrderScreeningsByIdWhenScreeningTimesAreEqual() {
        Screening first = createScreening(
                "Film One",
                "Cinema One",
                "2026-12-01T18:00:00"
        );

        Screening second = createScreening(
                "Film Two",
                "Cinema Two",
                "2026-12-01T18:00:00"
        );

        List<Screening> screenings =
                screeningRepository.findAllScreenings();

        assertEquals(2, screenings.size());
        assertEquals(first.screeningId(), screenings.get(0).screeningId());
        assertEquals(second.screeningId(), screenings.get(1).screeningId());
    }

    @Test
    void shouldReturnEmptyListWhenNoScreeningsExist() {
        List<Screening> screenings =
                screeningRepository.findAllScreenings();

        assertTrue(screenings.isEmpty());
    }

    @Test
    void shouldFindScreeningsByFilmId() {
        Film firstFilm = createFilm("First Film");
        Film secondFilm = createFilm("Second Film");

        createScreening(
                firstFilm.filmId(),
                "Cinema One",
                "2026-12-01T18:00:00"
        );

        createScreening(
                firstFilm.filmId(),
                "Cinema Two",
                "2026-12-02T18:00:00"
        );

        createScreening(
                secondFilm.filmId(),
                "Cinema Three",
                "2026-12-03T18:00:00"
        );

        List<Screening> screenings =
                screeningRepository.findScreeningsByFilmId(
                        firstFilm.filmId()
                );

        assertEquals(2, screenings.size());

        assertTrue(
                screenings.stream().allMatch(
                        screening -> screening.filmId().equals(firstFilm.filmId())
                )
        );

        assertEquals(
                List.of(
                        "2026-12-01T18:00:00",
                        "2026-12-02T18:00:00"
                ),
                screenings.stream()
                        .map(Screening::screeningTime)
                        .toList()
        );
    }

    @Test
    void shouldReturnEmptyListWhenFilmHasNoScreenings() {
        Film film = createFilm("Film Without Screenings");

        List<Screening> screenings =
                screeningRepository.findScreeningsByFilmId(film.filmId());

        assertTrue(screenings.isEmpty());
    }

    @Test
    void shouldFindScreeningsByVenue() {
        createScreening(
                "Film One",
                "Test Cinema",
                "2026-12-02T18:00:00"
        );

        createScreening(
                "Film Two",
                "Another Cinema",
                "2026-12-01T18:00:00"
        );

        createScreening(
                "Film Three",
                "Test Cinema",
                "2026-12-01T20:00:00"
        );

        List<Screening> screenings =
                screeningRepository.findScreeningsByVenue("Test Cinema");

        assertEquals(2, screenings.size());

        assertTrue(
                screenings.stream().allMatch(
                        screening -> screening.venue().equals("Test Cinema")
                )
        );

        assertEquals(
                List.of(
                        "2026-12-01T20:00:00",
                        "2026-12-02T18:00:00"
                ),
                screenings.stream()
                        .map(Screening::screeningTime)
                        .toList()
        );
    }

    @Test
    void shouldReturnEmptyListWhenVenueHasNoScreenings() {
        List<Screening> screenings =
                screeningRepository.findScreeningsByVenue("Unknown Cinema");

        assertTrue(screenings.isEmpty());
    }

    @Test
    void shouldUpdateScreeningAndReturnUpdatedDetails() {
        Screening createdScreening = createScreening(
                "Original Film",
                "Original Cinema",
                "2026-12-01T18:00:00"
        );

        Film replacementFilm = createFilm("Replacement Film");

        Screening updatedScreening =
                screeningRepository.updateScreening(
                        createdScreening.screeningId(),
                        new Screening(
                                null,
                                replacementFilm.filmId(),
                                null,
                                "Updated Cinema",
                                "2026-12-05T20:00:00"
                        )
                );

        assertEquals(
                createdScreening.screeningId(),
                updatedScreening.screeningId()
        );

        assertEquals(replacementFilm.filmId(), updatedScreening.filmId());
        assertEquals("Replacement Film", updatedScreening.filmTitle());
        assertEquals("Updated Cinema", updatedScreening.venue());
        assertEquals("2026-12-05T20:00:00", updatedScreening.screeningTime());
    }

    @Test
    void shouldThrowScreeningNotFoundExceptionWhenUpdatingNonexistentScreening() {
        Film film = createFilm("Test Film");

        assertThrows(
                ScreeningNotFoundException.class,
                () -> screeningRepository.updateScreening(
                        -1L,
                        new Screening(
                                null,
                                film.filmId(),
                                null,
                                "Test Cinema",
                                "2026-12-01T18:00:00"
                        )
                )
        );
    }

    @Test
    void shouldThrowFilmNotFoundExceptionWhenCreatingScreeningWithNonexistentFilm() throws SQLException {
        assertThrows(
                FilmNotFoundException.class,
                () -> screeningRepository.createScreening(
                        new Screening(
                                null,
                                999999L,
                                null,
                                "Test Cinema",
                                "2026-12-01T18:00:00"
                        )
                )
        );

        assertEquals(0, countScreenings());
    }

    @Test
    void shouldThrowFilmNotFoundExceptionWhenUpdatingScreeningWithNonexistentFilm() {
        Screening createdScreening = createScreening(
                "Test Film",
                "Test Cinema",
                "2026-12-01T18:00:00"
        );

        assertThrows(
                FilmNotFoundException.class,
                () -> screeningRepository.updateScreening(
                        createdScreening.screeningId(),
                        new Screening(
                                null,
                                999999L,
                                null,
                                "Updated Cinema",
                                "2026-12-05T20:00:00"
                        )
                )
        );

        Screening persistedScreening =
                screeningRepository.findScreeningById(
                        createdScreening.screeningId()
                ).orElseThrow();

        assertEquals(createdScreening, persistedScreening);
    }

    @Test
    void shouldDeleteExistingScreening() {
        Screening createdScreening = createScreening(
                "Test Film",
                "Test Cinema",
                "2026-12-01T18:00:00"
        );

        assertTrue(
                screeningRepository.deleteScreeningById(
                        createdScreening.screeningId()
                )
        );

        assertTrue(
                screeningRepository.findScreeningById(
                        createdScreening.screeningId()
                ).isEmpty()
        );
    }

    @Test
    void shouldReturnFalseWhenDeletingNonexistentScreening() {
        assertFalse(screeningRepository.deleteScreeningById(-1L));
    }

    @Test
    void shouldThrowRepositoryExceptionWhenCreatingNullScreening() {
        assertThrows(
                RepositoryException.class,
                () -> screeningRepository.createScreening(null)
        );
    }



    @Test
    void shouldThrowRepositoryExceptionWhenCreatingScreeningWithNullFilmId() {
        assertThrows(
                RepositoryException.class,
                () -> screeningRepository.createScreening(
                        new Screening(
                                null,
                                null,
                                null,
                                "Test Cinema",
                                "2026-12-01T18:00:00"
                        )
                )
        );
    }

    @Test
    void shouldThrowRepositoryExceptionWhenUpdatingNullScreening() {
        assertThrows(
                RepositoryException.class,
                () -> screeningRepository.updateScreening(1L, null)
        );
    }

    @Test
    void shouldThrowRepositoryExceptionWhenUpdatingScreeningWithNullFilmId() {
        assertThrows(
                RepositoryException.class,
                () -> screeningRepository.updateScreening(
                        1L,
                        new Screening(
                                null,
                                null,
                                null,
                                "Test Cinema",
                                "2026-12-01T18:00:00"
                        )
                )
        );
    }

    private Film createFilm(String title) {
        return filmRepository.createFilm(
                new Film(
                        null,
                        title,
                        2026,
                        null,
                        null,
                        List.of()
                )
        );
    }

    private Screening createScreening(
            String filmTitle,
            String venue,
            String screeningTime) {

        Film film = createFilm(filmTitle);

        return createScreening(
                film.filmId(),
                venue,
                screeningTime
        );
    }

    private Screening createScreening(
            Long filmId,
            String venue,
            String screeningTime) {

        return screeningRepository.createScreening(
                new Screening(
                        null,
                        filmId,
                        null,
                        venue,
                        screeningTime
                )
        );
    }

    private int countScreenings() throws SQLException {
        try (Connection connection = databaseManager.getConnection();
             Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery(
                     "SELECT COUNT(*) FROM screenings"
             )) {

            assertTrue(resultSet.next());
            return resultSet.getInt(1);
        }
    }
}