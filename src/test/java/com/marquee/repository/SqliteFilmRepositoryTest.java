package com.marquee.repository;

import com.marquee.db.DatabaseInitializer;
import com.marquee.db.DatabaseManager;
import com.marquee.db.transactions.ConnectionProvider;
import com.marquee.db.transactions.TransactionManager;
import com.marquee.exceptions.FilmNotFoundException;
import com.marquee.exceptions.GenreNotFoundException;
import com.marquee.exceptions.RepositoryException;
import com.marquee.model.Film;
import com.marquee.model.Genre;
import com.marquee.model.Page;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class SqliteFilmRepositoryTest {

    @TempDir
    Path tempDir;

    private DatabaseManager databaseManager;
    private FilmRepository filmRepository;
    private GenreRepository genreRepository;

    @BeforeEach
    void setUp() throws Exception {
        databaseManager = new DatabaseManager(
                tempDir.resolve("marquee.db")
        );

        DatabaseInitializer databaseInitializer =
                new DatabaseInitializer(databaseManager);

        databaseInitializer.initialize();

        // Remove seed records so every repository test starts with empty tables.
        try (Connection connection = databaseManager.getConnection();
             Statement statement = connection.createStatement()) {

            statement.executeUpdate("DELETE FROM films");
            statement.executeUpdate("DELETE FROM genres");
        }

        ConnectionProvider connectionProvider = databaseManager;

        TransactionManager transactionManager =
                new TransactionManager(connectionProvider);

        filmRepository = new SqliteFilmRepository(
                connectionProvider,
                transactionManager
        );

        genreRepository = new SqliteGenreRepository(
                connectionProvider,
                transactionManager
        );
    }

    @Test
    void shouldCreateFilmAndReturnGeneratedIdAndTimestamp() {
        Film film = new Film(
                null,
                "Test Film",
                2026,
                "A film created during testing.",
                null,
                List.of()
        );

        Film createdFilm = filmRepository.createFilm(film);

        assertNotNull(createdFilm.filmId());
        assertTrue(createdFilm.filmId() > 0);
        assertEquals("Test Film", createdFilm.title());
        assertEquals(2026, createdFilm.year());
        assertEquals("A film created during testing.", createdFilm.description());
        assertNotNull(createdFilm.createdAt());
        assertTrue(createdFilm.genres().isEmpty());
    }

    @Test
    void shouldCreateFilmWithNullDescription() {
        Film film = new Film(
                null,
                "Film Without Description",
                2026,
                null,
                null,
                List.of()
        );

        Film createdFilm = filmRepository.createFilm(film);

        assertNotNull(createdFilm.filmId());
        assertNull(createdFilm.description());
    }

    @Test
    void shouldCreateFilmWithAssociatedGenres() {
        Genre action = createGenre("Action");
        Genre comedy = createGenre("Comedy");

        Film createdFilm = filmRepository.createFilm(
                new Film(
                        null,
                        "Action Comedy",
                        2026,
                        "A film combining two genres.",
                        null,
                        List.of(action, comedy)
                )
        );

        assertEquals(2, createdFilm.genres().size());
        assertTrue(createdFilm.genres().contains(action));
        assertTrue(createdFilm.genres().contains(comedy));
    }

    @Test
    void shouldFindFilmByIdWithAssociatedGenres() {
        Genre action = createGenre("Action");

        Film createdFilm = filmRepository.createFilm(
                new Film(
                        null,
                        "Test Film",
                        2026,
                        "Test description.",
                        null,
                        List.of(action)
                )
        );

        Optional<Film> result = filmRepository.findFilmById(
                createdFilm.filmId()
        );

        assertTrue(result.isPresent());
        assertEquals(createdFilm, result.get());
        assertEquals(List.of(action), result.get().genres());
    }

    @Test
    void shouldReturnEmptyWhenFilmIdDoesNotExist() {
        Optional<Film> result = filmRepository.findFilmById(-1L);

        assertTrue(result.isEmpty());
    }

    @Test
    void shouldFindFilmWithoutGenres() {
        Film createdFilm = filmRepository.createFilm(
                new Film(
                        null,
                        "Film Without Genres",
                        2026,
                        null,
                        null,
                        List.of()
                )
        );

        Optional<Film> result = filmRepository.findFilmById(
                createdFilm.filmId()
        );

        assertTrue(result.isPresent());
        assertTrue(result.get().genres().isEmpty());
    }

    @Test
    void shouldReturnAllFilmsWithPaginationMetadata() {
        createFilm("Film One", 2020);
        createFilm("Film Two", 2021);
        createFilm("Film Three", 2022);

        Page<Film> result = filmRepository.findAllFilms(1, 10);

        assertEquals(1, result.page());
        assertEquals(10, result.perPage());
        assertEquals(3, result.total());
        assertEquals(1, result.totalPages());
        assertEquals(3, result.items().size());
    }

    @Test
    void shouldReturnOnlyFilmsBelongingToRequestedPage() {
        createFilm("Film One", 2020);
        createFilm("Film Two", 2021);
        createFilm("Film Three", 2022);
        createFilm("Film Four", 2023);
        createFilm("Film Five", 2024);

        Page<Film> firstPage = filmRepository.findAllFilms(1, 2);
        Page<Film> secondPage = filmRepository.findAllFilms(2, 2);
        Page<Film> thirdPage = filmRepository.findAllFilms(3, 2);

        assertEquals(5, firstPage.total());
        assertEquals(3, firstPage.totalPages());

        assertEquals(2, firstPage.items().size());
        assertEquals(2, secondPage.items().size());
        assertEquals(1, thirdPage.items().size());

        assertEquals(
                List.of("Film One", "Film Two"),
                firstPage.items().stream().map(Film::title).toList()
        );

        assertEquals(
                List.of("Film Three", "Film Four"),
                secondPage.items().stream().map(Film::title).toList()
        );

        assertEquals(
                List.of("Film Five"),
                thirdPage.items().stream().map(Film::title).toList()
        );
    }

    @Test
    void shouldReturnEmptyPageWhenPageIsBeyondAvailableFilms() {
        createFilm("Film One", 2020);

        Page<Film> result = filmRepository.findAllFilms(2, 10);

        assertEquals(1, result.total());
        assertEquals(0, result.items().size());
    }

    @Test
    void shouldReturnEmptyPageWhenNoFilmsExist() {
        Page<Film> result = filmRepository.findAllFilms(1, 10);

        assertEquals(0, result.total());
        assertEquals(0, result.totalPages());
        assertTrue(result.items().isEmpty());
    }

    @Test
    void shouldRejectInvalidPageNumber() {
        assertThrows(
                RepositoryException.class,
                () -> filmRepository.findAllFilms(0, 10)
        );
    }

    @Test
    void shouldRejectInvalidPageSize() {
        assertThrows(
                RepositoryException.class,
                () -> filmRepository.findAllFilms(1, 0)
        );
    }

    @Test
    void shouldUpdateFilmAndReplaceAssociatedGenres() {
        Genre originalGenre = createGenre("Original Genre");
        Genre replacementGenre = createGenre("Replacement Genre");

        Film createdFilm = filmRepository.createFilm(
                new Film(
                        null,
                        "Original Title",
                        2020,
                        "Original description.",
                        null,
                        List.of(originalGenre)
                )
        );

        Film updatedFilm = filmRepository.updateFilm(
                createdFilm.filmId(),
                new Film(
                        null,
                        "Updated Title",
                        2025,
                        "Updated description.",
                        null,
                        List.of(replacementGenre)
                )
        );

        assertEquals(createdFilm.filmId(), updatedFilm.filmId());
        assertEquals("Updated Title", updatedFilm.title());
        assertEquals(2025, updatedFilm.year());
        assertEquals("Updated description.", updatedFilm.description());
        assertEquals(List.of(replacementGenre), updatedFilm.genres());
    }

    @Test
    void shouldUpdateFilmDescriptionToNull() {
        Film createdFilm = createFilm("Test Film", 2020);

        Film updatedFilm = filmRepository.updateFilm(
                createdFilm.filmId(),
                new Film(
                        null,
                        "Test Film",
                        2020,
                        null,
                        null,
                        List.of()
                )
        );

        assertNull(updatedFilm.description());
    }

    @Test
    void shouldThrowFilmNotFoundExceptionWhenUpdatingNonexistentFilm() {
        assertThrows(
                FilmNotFoundException.class,
                () -> filmRepository.updateFilm(
                        -1L,
                        new Film(
                                null,
                                "Updated Film",
                                2026,
                                null,
                                null,
                                List.of()
                        )
                )
        );
    }

    @Test
    void shouldThrowGenreNotFoundExceptionWhenCreatingFilmWithNonexistentGenre() throws Exception {
        assertThrows(
                GenreNotFoundException.class,
                () -> filmRepository.createFilm(
                        new Film(
                                null,
                                "Invalid Film",
                                2026,
                                null,
                                null,
                                List.of(new Genre(999999L, "Missing Genre"))
                        )
                )
        );

        assertEquals(0, countFilms());
    }

    @Test
    void shouldRollbackFilmUpdateWhenReplacementGenreDoesNotExist() {
        Genre originalGenre = createGenre("Original Genre");

        Film createdFilm = filmRepository.createFilm(
                new Film(
                        null,
                        "Original Title",
                        2020,
                        "Original description.",
                        null,
                        List.of(originalGenre)
                )
        );

        assertThrows(
                GenreNotFoundException.class,
                () -> filmRepository.updateFilm(
                        createdFilm.filmId(),
                        new Film(
                                null,
                                "Updated Title",
                                2026,
                                "Updated description.",
                                null,
                                List.of(new Genre(999999L, "Missing Genre"))
                        )
                )
        );

        Film persistedFilm = filmRepository.findFilmById(
                createdFilm.filmId()
        ).orElseThrow();

        assertEquals("Original Title", persistedFilm.title());
        assertEquals(2020, persistedFilm.year());
        assertEquals("Original description.", persistedFilm.description());
        assertEquals(List.of(originalGenre), persistedFilm.genres());
    }

    @Test
    void shouldDeleteExistingFilmAndItsGenreAssociations() throws Exception {
        Genre action = createGenre("Action");

        Film createdFilm = filmRepository.createFilm(
                new Film(
                        null,
                        "Test Film",
                        2026,
                        null,
                        null,
                        List.of(action)
                )
        );

        assertTrue(filmRepository.deleteFilmById(createdFilm.filmId()));

        assertTrue(
                filmRepository.findFilmById(createdFilm.filmId()).isEmpty()
        );

        assertEquals(0, countGenreAssociations(createdFilm.filmId()));

        // Deleting the film must not delete the genre itself.
        assertTrue(genreRepository.findGenreById(action.genreId()).isPresent());
    }

    @Test
    void shouldReturnFalseWhenDeletingNonexistentFilm() {
        assertFalse(filmRepository.deleteFilmById(-1L));
    }

    @Test
    void shouldThrowRepositoryExceptionWhenCreatingNullFilm() {
        assertThrows(
                RepositoryException.class,
                () -> filmRepository.createFilm(null)
        );
    }

    @Test
    void shouldThrowRepositoryExceptionWhenUpdatingWithNullFilm() {
        assertThrows(
                RepositoryException.class,
                () -> filmRepository.updateFilm(1L, null)
        );
    }

    private Genre createGenre(String name) {
        return genreRepository.createGenre(new Genre(null, name));
    }

    private Film createFilm(String title, int year) {
        return filmRepository.createFilm(
                new Film(
                        null,
                        title,
                        year,
                        null,
                        null,
                        List.of()
                )
        );
    }

    private int countFilms() throws Exception {
        try (Connection connection = databaseManager.getConnection();
             Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery(
                     "SELECT COUNT(*) FROM films"
             )) {

            assertTrue(resultSet.next());
            return resultSet.getInt(1);
        }
    }

    private int countGenreAssociations(long filmId) throws Exception {
        String sql = """
                SELECT COUNT(*)
                FROM film_genres
                WHERE film_id = %d
                """.formatted(filmId);

        try (Connection connection = databaseManager.getConnection();
             Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery(sql)) {

            assertTrue(resultSet.next());
            return resultSet.getInt(1);
        }
    }
}