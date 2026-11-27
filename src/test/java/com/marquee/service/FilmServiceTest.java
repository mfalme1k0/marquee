package com.marquee.service;

import com.marquee.exceptions.DuplicateGenreException;
import com.marquee.exceptions.FilmNotFoundException;
import com.marquee.exceptions.GenreNotFoundException;
import com.marquee.exceptions.ValidationException;
import com.marquee.logging.RecordingLogger;
import com.marquee.model.Film;
import com.marquee.model.Genre;
import com.marquee.model.Page;
import com.marquee.repository.FilmRepository;
import com.marquee.repository.GenreRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class FilmServiceTest {

    private FakeFilmRepository filmRepository;
    private FakeGenreRepository genreRepository;
    private FilmService filmService;
    private RecordingLogger logger;

    private static final String CREATED_AT =
            "2026-11-18T10:00:00Z";

    @BeforeEach
    void setUp() {
        filmRepository = new FakeFilmRepository();
        genreRepository = new FakeGenreRepository();
        filmRepository.genreRepository = genreRepository;

        logger = new RecordingLogger();

        filmService = new FilmService(
                filmRepository,
                genreRepository,
                logger
        );
    }

    // ---------------------------------------------------------
    // CONSTRUCTOR
    // ---------------------------------------------------------

    @Test
    void shouldCreateServiceWithValidRepositories() {
        assertNotNull(
                new FilmService(filmRepository, genreRepository, logger)
        );
    }

    @Test
    void shouldRejectNullFilmRepository() {
        assertThrows(
                NullPointerException.class,
                () -> new FilmService(null, genreRepository, logger)
        );
    }

    @Test
    void shouldRejectNullGenreRepository() {
        assertThrows(
                NullPointerException.class,
                () -> new FilmService(filmRepository, null, logger)
        );
    }

    // ---------------------------------------------------------
    // CREATE FILM
    // ---------------------------------------------------------

    @Test
    void shouldCreateFilmSuccessfully() {

        Film input = new Film(
                99L,
                "  Inception  ",
                2010,
                "  A science fiction film.  ",
                CREATED_AT,
                List.of()
        );

        Film result = filmService.createFilm(input);

        assertNotNull(result);
        assertEquals(1L, result.filmId());
        assertEquals("Inception", result.title());
        assertEquals(2010, result.year());
        assertEquals(
                "A science fiction film.",
                result.description()
        );

        // The service must not supply persistence-managed fields.
        assertNull(filmRepository.lastCreatedFilm.filmId());
        assertNull(filmRepository.lastCreatedFilm.createdAt());
    }

    @Test
    void shouldRejectNullFilm() {

        ValidationException exception = assertThrows(
                ValidationException.class,
                () -> filmService.createFilm(null)
        );

        assertEquals(
                List.of("Film must not be null"),
                exception.getDetails()
        );
    }

    @Test
    void shouldRejectBlankFilmTitle() {

        Film film = film(null, "   ", 2020, null, List.of());

        assertThrows(
                ValidationException.class,
                () -> filmService.createFilm(film)
        );
    }

    @Test
    void shouldRejectNullFilmTitle() {

        Film film = film(null, null, 2020, null, List.of());

        assertThrows(
                ValidationException.class,
                () -> filmService.createFilm(film)
        );
    }

    @Test
    void shouldRejectFilmTitleLongerThan255Characters() {

        String title = "A".repeat(256);

        Film film = film(null, title, 2020, null, List.of());

        assertThrows(
                ValidationException.class,
                () -> filmService.createFilm(film)
        );
    }

    @Test
    void shouldAcceptFilmTitleOf255Characters() {

        String title = "A".repeat(255);

        Film film = film(null, title, 2020, null, List.of());

        assertDoesNotThrow(
                () -> filmService.createFilm(film)
        );
    }

    @Test
    void shouldRejectNullFilmYear() {

        Film film = film(null, "Inception", null, null, List.of());

        assertThrows(
                ValidationException.class,
                () -> filmService.createFilm(film)
        );
    }

    @Test
    void shouldRejectYearEarlierThanMinimum() {

        Film film = film(null, "Old Film", 1887, null, List.of());

        assertThrows(
                ValidationException.class,
                () -> filmService.createFilm(film)
        );
    }

    @Test
    void shouldAcceptMinimumFilmYear() {

        Film film = film(null, "Early Film", 1888, null, List.of());

        assertDoesNotThrow(
                () -> filmService.createFilm(film)
        );
    }

    @Test
    void shouldRejectDescriptionLongerThan5000Characters() {

        String description = "A".repeat(5001);

        Film film = film(
                null,
                "Inception",
                2010,
                description,
                List.of()
        );

        assertThrows(
                ValidationException.class,
                () -> filmService.createFilm(film)
        );
    }

    @Test
    void shouldAcceptDescriptionOf5000Characters() {

        String description = "A".repeat(5000);

        Film film = film(
                null,
                "Inception",
                2010,
                description,
                List.of()
        );

        assertDoesNotThrow(
                () -> filmService.createFilm(film)
        );
    }

    @Test
    void shouldConvertBlankDescriptionToNull() {

        Film film = film(
                null,
                "Inception",
                2010,
                "   ",
                List.of()
        );

        Film result = filmService.createFilm(film);

        assertNull(result.description());
    }

    @Test
    void shouldPreserveNullDescription() {

        Film film = film(
                null,
                "Inception",
                2010,
                null,
                List.of()
        );

        Film result = filmService.createFilm(film);

        assertNull(result.description());
    }

    // ---------------------------------------------------------
    // FILM GENRES
    // ---------------------------------------------------------

    @Test
    void shouldCreateFilmWithExistingGenres() {

        Genre action = genreRepository.createGenre(
                new Genre(null, "Action")
        );

        Genre comedy = genreRepository.createGenre(
                new Genre(null, "Comedy")
        );

        Film input = film(
                null,
                "Action Comedy",
                2020,
                null,
                List.of(action, comedy)
        );

        Film result = filmService.createFilm(input);

        assertEquals(2, result.genres().size());
        assertEquals(action, result.genres().get(0));
        assertEquals(comedy, result.genres().get(1));
    }

    @Test
    void shouldRejectGenreWithoutPersistedId() {

        Genre genre = new Genre(null, "Action");

        Film input = film(
                null,
                "Inception",
                2010,
                null,
                List.of(genre)
        );

        assertThrows(
                ValidationException.class,
                () -> filmService.createFilm(input)
        );
    }

    @Test
    void shouldRejectGenreWithInvalidId() {

        Genre genre = new Genre(0L, "Action");

        Film input = film(
                null,
                "Inception",
                2010,
                null,
                List.of(genre)
        );

        assertThrows(
                ValidationException.class,
                () -> filmService.createFilm(input)
        );
    }

    @Test
    void shouldRejectDuplicateGenreIds() {

        Genre action = genreRepository.createGenre(
                new Genre(null, "Action")
        );

        Film input = film(
                null,
                "Inception",
                2010,
                null,
                List.of(action, action)
        );

        assertThrows(
                ValidationException.class,
                () -> filmService.createFilm(input)
        );
    }

    @Test
    void shouldRejectGenreThatDoesNotExist() {

        Genre missingGenre = new Genre(999L, "Unknown");

        Film input = film(
                null,
                "Inception",
                2010,
                null,
                List.of(missingGenre)
        );

        assertThrows(
                GenreNotFoundException.class,
                () -> filmService.createFilm(input)
        );
    }

    // ---------------------------------------------------------
    // GET FILM BY ID
    // ---------------------------------------------------------

    @Test
    void shouldRetrieveExistingFilm() {

        Film created = filmService.createFilm(
                film(null, "Inception", 2010, null, List.of())
        );

        Film result = filmService.getFilmById(created.filmId());

        assertEquals(created, result);
    }

    @Test
    void shouldRejectInvalidFilmIdWhenRetrieving() {

        assertThrows(
                ValidationException.class,
                () -> filmService.getFilmById(0)
        );
    }

    @Test
    void shouldRejectNegativeFilmIdWhenRetrieving() {

        assertThrows(
                ValidationException.class,
                () -> filmService.getFilmById(-1)
        );
    }

    @Test
    void shouldThrowWhenFilmDoesNotExist() {

        assertThrows(
                FilmNotFoundException.class,
                () -> filmService.getFilmById(999)
        );
    }

    // ---------------------------------------------------------
    // GET ALL FILMS
    // ---------------------------------------------------------

    @Test
    void shouldRetrieveFilmsWithPagination() {

        filmService.createFilm(
                film(null, "Film One", 2020, null, List.of())
        );

        filmService.createFilm(
                film(null, "Film Two", 2021, null, List.of())
        );

        Page<Film> result = filmService.getAllFilms(1, 10);

        assertEquals(1, result.page());
        assertEquals(10, result.perPage());
        assertEquals(2, result.total());
        assertEquals(2, result.items().size());
    }

    @Test
    void shouldRejectPageNumberBelowOne() {

        assertThrows(
                ValidationException.class,
                () -> filmService.getAllFilms(0, 10)
        );
    }

    @Test
    void shouldRejectPageSizeBelowOne() {

        assertThrows(
                ValidationException.class,
                () -> filmService.getAllFilms(1, 0)
        );
    }

    @Test
    void shouldRejectPageSizeAbove100() {

        assertThrows(
                ValidationException.class,
                () -> filmService.getAllFilms(1, 101)
        );
    }

    @Test
    void shouldCollectMultiplePaginationErrors() {

        ValidationException exception = assertThrows(
                ValidationException.class,
                () -> filmService.getAllFilms(0, 101)
        );

        assertEquals(2, exception.getDetails().size());
    }

    // ---------------------------------------------------------
    // UPDATE FILM
    // ---------------------------------------------------------

    @Test
    void shouldUpdateExistingFilm() {

        Film created = filmService.createFilm(
                film(null, "Original", 2020, null, List.of())
        );

        Film replacement = film(
                null,
                "Updated",
                2025,
                "Updated description",
                List.of()
        );

        Film result = filmService.updateFilm(
                created.filmId(),
                replacement
        );

        assertEquals(created.filmId(), result.filmId());
        assertEquals("Updated", result.title());
        assertEquals(2025, result.year());
    }

    @Test
    void shouldRejectInvalidIdWhenUpdating() {

        assertThrows(
                ValidationException.class,
                () -> filmService.updateFilm(
                        0,
                        film(null, "Updated", 2020, null, List.of())
                )
        );
    }

    @Test
    void shouldRejectNullFilmWhenUpdating() {

        Film created = filmService.createFilm(
                film(null, "Original", 2020, null, List.of())
        );

        assertThrows(
                ValidationException.class,
                () -> filmService.updateFilm(created.filmId(), null)
        );
    }

    @Test
    void shouldThrowWhenUpdatingNonexistentFilm() {

        assertThrows(
                FilmNotFoundException.class,
                () -> filmService.updateFilm(
                        999,
                        film(null, "Updated", 2020, null, List.of())
                )
        );
    }

    @Test
    void shouldValidateGenresWhenUpdatingFilm() {

        Film created = filmService.createFilm(
                film(null, "Original", 2020, null, List.of())
        );

        Genre missingGenre = new Genre(999L, "Unknown");

        assertThrows(
                GenreNotFoundException.class,
                () -> filmService.updateFilm(
                        created.filmId(),
                        film(
                                null,
                                "Updated",
                                2020,
                                null,
                                List.of(missingGenre)
                        )
                )
        );
    }

    // ---------------------------------------------------------
    // DELETE FILM
    // ---------------------------------------------------------

    @Test
    void shouldDeleteExistingFilm() {

        Film created = filmService.createFilm(
                film(null, "Inception", 2010, null, List.of())
        );

        assertTrue(
                filmService.deleteFilm(created.filmId())
        );

        assertTrue(
                filmRepository.findFilmById(created.filmId()).isEmpty()
        );
    }

    @Test
    void shouldReturnFalseWhenDeletingMissingFilm() {

        assertFalse(
                filmService.deleteFilm(999)
        );
    }

    @Test
    void shouldRejectInvalidIdWhenDeletingFilm() {

        assertThrows(
                ValidationException.class,
                () -> filmService.deleteFilm(0)
        );
    }

    // ---------------------------------------------------------
    // TEST HELPERS
    // ---------------------------------------------------------

    private Film film(
            Long id,
            String title,
            Integer year,
            String description,
            List<Genre> genres
    ) {
        return new Film(
                id,
                title,
                year,
                description,
                null,
                genres
        );
    }

    // ---------------------------------------------------------
    // HELPERS FOR FILTER AND GENRE-ASSIGNMENT TESTS
    // ---------------------------------------------------------

    private Genre addGenre(String name) {
        return genreRepository.createGenre(new Genre(null, name));
    }

    private Film addFilm(String title, int year, Genre... genres) {
        return filmService.createFilm(
                new Film(null, title, year, null, null, List.of(genres))
        );
    }

    // ---------------------------------------------------------
    // SEARCH BY TITLE
    // ---------------------------------------------------------

    @Test
    void shouldFindFilmsByTitle() {
        addFilm("Inception", 2010);
        addFilm("The Matrix", 1999);

        Page<Film> result = filmService.getFilmsByTitle("incep", 1, 20);

        assertEquals(1, result.items().size());
        assertEquals(1, result.total());
        assertEquals("Inception", result.items().getFirst().title());
    }

    @Test
    void shouldTrimTitleWhenSearching() {
        addFilm("The Matrix", 1999);

        assertEquals(
                1,
                filmService.getFilmsByTitle("  Matrix  ", 1, 20).items().size()
        );
    }

    @Test
    void shouldReturnEmptyPageWhenNoTitleMatches() {
        addFilm("Inception", 2010);

        Page<Film> result = filmService.getFilmsByTitle("Alien", 1, 20);

        assertTrue(result.items().isEmpty());
        assertEquals(0, result.total());
    }

    @Test
    void shouldRejectNullTitleWhenSearching() {
        assertThrows(
                ValidationException.class,
                () -> filmService.getFilmsByTitle(null, 1, 20)
        );
    }

    @Test
    void shouldRejectBlankTitleWhenSearching() {
        assertThrows(
                ValidationException.class,
                () -> filmService.getFilmsByTitle("   ", 1, 20)
        );
    }

    @Test
    void shouldRejectTitleLongerThanMaximumWhenSearching() {
        assertThrows(
                ValidationException.class,
                () -> filmService.getFilmsByTitle("A".repeat(256), 1, 20)
        );
    }

    @Test
    void shouldAcceptTitleAtMaximumLengthWhenSearching() {
        assertDoesNotThrow(
                () -> filmService.getFilmsByTitle("A".repeat(255), 1, 20)
        );
    }

    @Test
    void shouldRejectInvalidPaginationWhenSearchingByTitle() {
        assertThrows(
                ValidationException.class,
                () -> filmService.getFilmsByTitle("Matrix", 0, 20)
        );
    }

    // ---------------------------------------------------------
    // FILTER BY YEAR
    // ---------------------------------------------------------

    @Test
    void shouldFindFilmsByYear() {
        addFilm("Inception", 2010);
        addFilm("The Matrix", 1999);

        Page<Film> result = filmService.getFilmsByYear(2010, 1, 20);

        assertEquals(1, result.items().size());
        assertEquals("Inception", result.items().getFirst().title());
    }

    @Test
    void shouldReturnEmptyPageWhenNoFilmInYear() {
        addFilm("Inception", 2010);

        Page<Film> result = filmService.getFilmsByYear(2000, 1, 20);

        assertTrue(result.items().isEmpty());
        assertEquals(0, result.total());
    }

    @Test
    void shouldAcceptYearEarlierThanFirstFilmWhenFiltering() {
        Page<Film> result = filmService.getFilmsByYear(1800, 1, 20);

        assertTrue(result.items().isEmpty());
    }

    @Test
    void shouldRejectZeroYearWhenFiltering() {
        assertThrows(
                ValidationException.class,
                () -> filmService.getFilmsByYear(0, 1, 20)
        );
    }

    @Test
    void shouldRejectNegativeYearWhenFiltering() {
        assertThrows(
                ValidationException.class,
                () -> filmService.getFilmsByYear(-2010, 1, 20)
        );
    }

    @Test
    void shouldRejectInvalidPaginationWhenFilteringByYear() {
        assertThrows(
                ValidationException.class,
                () -> filmService.getFilmsByYear(2010, 1, 101)
        );
    }

    // ---------------------------------------------------------
    // FILTER BY GENRE ID
    // ---------------------------------------------------------

    @Test
    void shouldFindFilmsByGenreId() {
        Genre action = addGenre("Action");
        addFilm("Die Hard", 1988, action);
        addFilm("Amelie", 2001);

        Page<Film> result =
                filmService.getFilmsByGenreId(action.genreId(), 1, 20);

        assertEquals(1, result.items().size());
        assertEquals("Die Hard", result.items().getFirst().title());
    }

    @Test
    void shouldReturnEmptyPageWhenGenreHasNoFilms() {
        Genre horror = addGenre("Horror");

        Page<Film> result =
                filmService.getFilmsByGenreId(horror.genreId(), 1, 20);

        assertTrue(result.items().isEmpty());
        assertEquals(0, result.total());
    }

    @Test
    void shouldRejectZeroGenreIdWhenFiltering() {
        assertThrows(
                ValidationException.class,
                () -> filmService.getFilmsByGenreId(0, 1, 20)
        );
    }

    @Test
    void shouldRejectNegativeGenreIdWhenFiltering() {
        assertThrows(
                ValidationException.class,
                () -> filmService.getFilmsByGenreId(-1, 1, 20)
        );
    }

    @Test
    void shouldRejectInvalidPaginationWhenFilteringByGenreId() {
        assertThrows(
                ValidationException.class,
                () -> filmService.getFilmsByGenreId(1, 1, 0)
        );
    }

    // ---------------------------------------------------------
    // FILTER BY GENRE NAME
    // ---------------------------------------------------------

    @Test
    void shouldFindFilmsByGenreName() {
        Genre action = addGenre("Action");
        addFilm("Die Hard", 1988, action);
        addFilm("Amelie", 2001);

        Page<Film> result = filmService.getFilmsByGenreName("Action", 1, 20);

        assertEquals(1, result.items().size());
        assertEquals("Die Hard", result.items().getFirst().title());
    }

    @Test
    void shouldTrimGenreNameWhenFiltering() {
        Genre action = addGenre("Action");
        addFilm("Die Hard", 1988, action);

        assertEquals(
                1,
                filmService.getFilmsByGenreName("  Action  ", 1, 20)
                        .items().size()
        );
    }

    @Test
    void shouldReturnEmptyPageForUnknownGenreName() {
        addFilm("Amelie", 2001);

        Page<Film> result = filmService.getFilmsByGenreName("Horror", 2, 10);

        assertTrue(result.items().isEmpty());
        assertEquals(0, result.total());
        assertEquals(2, result.page());
        assertEquals(10, result.perPage());
    }

    @Test
    void shouldRejectNullGenreNameWhenFiltering() {
        assertThrows(
                ValidationException.class,
                () -> filmService.getFilmsByGenreName(null, 1, 20)
        );
    }

    @Test
    void shouldRejectBlankGenreNameWhenFiltering() {
        assertThrows(
                ValidationException.class,
                () -> filmService.getFilmsByGenreName("  ", 1, 20)
        );
    }

    @Test
    void shouldRejectInvalidPaginationWhenFilteringByGenreName() {
        assertThrows(
                ValidationException.class,
                () -> filmService.getFilmsByGenreName("Action", 0, 20)
        );
    }

    // ---------------------------------------------------------
    // ASSIGN GENRE TO FILM
    // ---------------------------------------------------------

    @Test
    void shouldAssignGenreToFilm() {
        Genre action = addGenre("Action");
        Film film = addFilm("Die Hard", 1988);

        assertTrue(filmService.assignGenreToFilm(film.filmId(), action.genreId()));

        assertEquals(
                List.of(action),
                filmService.getFilmById(film.filmId()).genres()
        );
    }

    @Test
    void shouldReturnFalseWhenGenreAlreadyAssigned() {
        Genre action = addGenre("Action");
        Film film = addFilm("Die Hard", 1988, action);

        assertFalse(filmService.assignGenreToFilm(film.filmId(), action.genreId()));
        assertEquals(
                1,
                filmService.getFilmById(film.filmId()).genres().size()
        );
    }

    @Test
    void shouldRejectInvalidFilmIdWhenAssigningGenre() {
        assertThrows(
                ValidationException.class,
                () -> filmService.assignGenreToFilm(0, 1)
        );
    }

    @Test
    void shouldRejectInvalidGenreIdWhenAssigningGenre() {
        assertThrows(
                ValidationException.class,
                () -> filmService.assignGenreToFilm(1, -1)
        );
    }

    @Test
    void shouldThrowWhenAssigningGenreToUnknownFilm() {
        Genre action = addGenre("Action");

        assertThrows(
                FilmNotFoundException.class,
                () -> filmService.assignGenreToFilm(999, action.genreId())
        );
    }

    @Test
    void shouldThrowWhenAssigningUnknownGenre() {
        Film film = addFilm("Die Hard", 1988);

        assertThrows(
                GenreNotFoundException.class,
                () -> filmService.assignGenreToFilm(film.filmId(), 999)
        );
    }

    // ---------------------------------------------------------
    // REMOVE GENRE FROM FILM
    // ---------------------------------------------------------

    @Test
    void shouldRemoveGenreFromFilm() {
        Genre action = addGenre("Action");
        Film film = addFilm("Die Hard", 1988, action);

        assertTrue(
                filmService.removeGenreFromFilm(film.filmId(), action.genreId())
        );
        assertTrue(filmService.getFilmById(film.filmId()).genres().isEmpty());
    }

    @Test
    void shouldReturnFalseWhenGenreWasNotAssigned() {
        Genre action = addGenre("Action");
        Film film = addFilm("Die Hard", 1988);

        assertFalse(
                filmService.removeGenreFromFilm(film.filmId(), action.genreId())
        );
    }

    @Test
    void shouldRejectInvalidFilmIdWhenRemovingGenre() {
        assertThrows(
                ValidationException.class,
                () -> filmService.removeGenreFromFilm(0, 1)
        );
    }

    @Test
    void shouldRejectInvalidGenreIdWhenRemovingGenre() {
        assertThrows(
                ValidationException.class,
                () -> filmService.removeGenreFromFilm(1, 0)
        );
    }

    @Test
    void shouldThrowWhenRemovingGenreFromUnknownFilm() {
        Genre action = addGenre("Action");

        assertThrows(
                FilmNotFoundException.class,
                () -> filmService.removeGenreFromFilm(999, action.genreId())
        );
    }

    @Test
    void shouldThrowWhenRemovingUnknownGenre() {
        Film film = addFilm("Die Hard", 1988);

        assertThrows(
                GenreNotFoundException.class,
                () -> filmService.removeGenreFromFilm(film.filmId(), 999)
        );
    }

    // ---------------------------------------------------------
    // LOGGING
    // ---------------------------------------------------------

    @Test
    void shouldRejectNullLogger() {
        assertThrows(
                NullPointerException.class,
                () -> new FilmService(filmRepository, genreRepository, null)
        );
    }

    @Test
    void shouldLogFilmCreated() {
        Film created = filmService.createFilm(
                film(null, "  Inception  ", 2010, null, List.of())
        );

        RecordingLogger.Event event = logger.only("film.created");
        assertEquals("INFO", event.level());
        assertNull(event.userId());
        assertEquals(created.filmId(), event.context().get("film_id"));
        assertEquals("Inception", event.context().get("title"));
        assertEquals(List.of("film.created"), logger.eventNames());
    }

    @Test
    void shouldLogWarningWhenFilmCreationFailsValidation() {
        assertThrows(
                ValidationException.class,
                () -> filmService.createFilm(film(null, "  ", 2010, null, List.of()))
        );

        RecordingLogger.Event event = logger.only("film.create.rejected");
        assertEquals("WARN", event.level());
        assertEquals("ValidationException", event.context().get("reason"));
        assertEquals(List.of("film.create.rejected"), logger.eventNames());
    }

    @Test
    void shouldLogWarningWhenCreatingNullFilm() {
        assertThrows(ValidationException.class, () -> filmService.createFilm(null));

        RecordingLogger.Event event = logger.only("film.create.rejected");
        assertEquals("null", event.context().get("title"));
    }

    @Test
    void shouldLogWarningWhenFilmCreationReferencesUnknownGenre() {
        assertThrows(
                GenreNotFoundException.class,
                () -> filmService.createFilm(
                        film(null, "Heat", 1995, null, List.of(new Genre(99L, "Crime")))
                )
        );

        RecordingLogger.Event event = logger.only("film.create.rejected");
        assertEquals("GenreNotFoundException", event.context().get("reason"));
        assertEquals("Heat", event.context().get("title"));
    }

    @Test
    void shouldNotLogFilmCreatedWhenRepositoryFails() {
        FilmRepository failing = new FakeFilmRepository() {
            @Override
            public Film createFilm(Film film) {
                throw new IllegalStateException("db down");
            }
        };
        FilmService service = new FilmService(failing, genreRepository, logger);

        assertThrows(
                IllegalStateException.class,
                () -> service.createFilm(film(null, "Heat", 1995, null, List.of()))
        );

        assertTrue(logger.isEmpty());
    }

    @Test
    void shouldLogFilmUpdated() {
        Film created = addFilm("Heat", 1995);
        logger.clear();

        filmService.updateFilm(created.filmId(), film(null, "Heat (Director's Cut)", 1995, null, List.of()));

        RecordingLogger.Event event = logger.only("film.updated");
        assertEquals("INFO", event.level());
        assertEquals(created.filmId(), event.context().get("film_id"));
        assertEquals("Heat (Director's Cut)", event.context().get("title"));
    }

    @Test
    void shouldLogWarningWhenFilmUpdateFailsValidation() {
        Film created = addFilm("Heat", 1995);
        logger.clear();

        assertThrows(
                ValidationException.class,
                () -> filmService.updateFilm(created.filmId(), film(null, "Heat", 1800, null, List.of()))
        );

        RecordingLogger.Event event = logger.only("film.update.rejected");
        assertEquals("WARN", event.level());
        assertEquals("ValidationException", event.context().get("reason"));
        assertEquals(created.filmId(), event.context().get("film_id"));
        assertEquals(List.of("film.update.rejected"), logger.eventNames());
    }

    @Test
    void shouldLogWarningWhenFilmUpdateHasInvalidId() {
        assertThrows(
                ValidationException.class,
                () -> filmService.updateFilm(0, film(null, "Heat", 1995, null, List.of()))
        );

        assertEquals(0L, logger.only("film.update.rejected").context().get("film_id"));
    }

    @Test
    void shouldNotLogFilmUpdatedWhenFilmDoesNotExist() {
        assertThrows(
                FilmNotFoundException.class,
                () -> filmService.updateFilm(999, film(null, "Heat", 1995, null, List.of()))
        );

        assertFalse(logger.eventNames().contains("film.updated"));
    }

    @Test
    void shouldLogFilmDeleted() {
        Film created = addFilm("Heat", 1995);
        logger.clear();

        assertTrue(filmService.deleteFilm(created.filmId()));

        RecordingLogger.Event event = logger.only("film.deleted");
        assertEquals("INFO", event.level());
        assertEquals(created.filmId(), event.context().get("film_id"));
        assertEquals(List.of("film.deleted"), logger.eventNames());
    }

    @Test
    void shouldLogWarningWhenDeletingMissingFilm() {
        assertFalse(filmService.deleteFilm(999));

        RecordingLogger.Event event = logger.only("film.delete.not_found");
        assertEquals("WARN", event.level());
        assertEquals(999L, event.context().get("film_id"));
        assertEquals(List.of("film.delete.not_found"), logger.eventNames());
    }

    @Test
    void shouldLogWarningWhenFilmIsNotFoundById() {
        assertThrows(FilmNotFoundException.class, () -> filmService.getFilmById(42));

        RecordingLogger.Event event = logger.only("film.not_found");
        assertEquals("WARN", event.level());
        assertEquals(42L, event.context().get("film_id"));
    }

    @Test
    void shouldNotLogWhenFilmIsFoundById() {
        Film created = addFilm("Heat", 1995);
        logger.clear();

        filmService.getFilmById(created.filmId());

        assertTrue(logger.isEmpty());
    }

    @Test
    void shouldNotLogForReadOnlyListings() {
        addFilm("Heat", 1995);
        logger.clear();

        filmService.getAllFilms(1, 10);
        filmService.getFilmsByTitle("heat", 1, 10);
        filmService.getFilmsByYear(1995, 1, 10);

        assertTrue(logger.isEmpty());
    }

    @Test
    void shouldLogGenreAssignedToFilm() {
        Film created = addFilm("Heat", 1995);
        Genre crime = addGenre("Crime");
        logger.clear();

        assertTrue(filmService.assignGenreToFilm(created.filmId(), crime.genreId()));

        RecordingLogger.Event event = logger.only("film.genre.assigned");
        assertEquals("INFO", event.level());
        assertEquals(created.filmId(), event.context().get("film_id"));
        assertEquals(crime.genreId(), event.context().get("genre_id"));
    }

    @Test
    void shouldLogWarningWhenGenreIsAlreadyAssigned() {
        Film created = addFilm("Heat", 1995);
        Genre crime = addGenre("Crime");
        filmService.assignGenreToFilm(created.filmId(), crime.genreId());
        logger.clear();

        assertFalse(filmService.assignGenreToFilm(created.filmId(), crime.genreId()));

        RecordingLogger.Event event = logger.only("film.genre.assign.rejected");
        assertEquals("WARN", event.level());
        assertEquals("already_assigned", event.context().get("reason"));
        assertEquals(List.of("film.genre.assign.rejected"), logger.eventNames());
    }

    @Test
    void shouldLogGenreRemovedFromFilm() {
        Film created = addFilm("Heat", 1995);
        Genre crime = addGenre("Crime");
        filmService.assignGenreToFilm(created.filmId(), crime.genreId());
        logger.clear();

        assertTrue(filmService.removeGenreFromFilm(created.filmId(), crime.genreId()));

        RecordingLogger.Event event = logger.only("film.genre.removed");
        assertEquals("INFO", event.level());
        assertEquals(created.filmId(), event.context().get("film_id"));
        assertEquals(crime.genreId(), event.context().get("genre_id"));
    }

    @Test
    void shouldLogWarningWhenRemovingGenreThatWasNotAssigned() {
        Film created = addFilm("Heat", 1995);
        Genre crime = addGenre("Crime");
        logger.clear();

        assertFalse(filmService.removeGenreFromFilm(created.filmId(), crime.genreId()));

        RecordingLogger.Event event = logger.only("film.genre.remove.rejected");
        assertEquals("WARN", event.level());
        assertEquals("not_assigned", event.context().get("reason"));
    }

    @Test
    void shouldNotLeakDescriptionIntoFilmLogs() {
        filmService.createFilm(film(null, "Heat", 1995, "A very long secret synopsis", List.of()));

        assertFalse(logger.only("film.created").context().containsKey("description"));
    }

    // ---------------------------------------------------------
    // IN-MEMORY FILM REPOSITORY
    // ---------------------------------------------------------

    private static class FakeFilmRepository
            implements FilmRepository {

        private final Map<Long, Film> films =
                new LinkedHashMap<>();

        private long nextId = 1;

        private Film lastCreatedFilm;

        // Set by the test so assign/remove can check genres exist.
        private GenreRepository genreRepository;

        @Override
        public Film createFilm(Film film) {

            lastCreatedFilm = film;

            long id = nextId++;

            Film persisted = new Film(
                    id,
                    film.title(),
                    film.year(),
                    film.description(),
                    CREATED_AT,
                    film.genres()
            );

            films.put(id, persisted);

            return persisted;
        }

        @Override
        public Optional<Film> findFilmById(long filmId) {
            return Optional.ofNullable(films.get(filmId));
        }

        @Override
        public Page<Film> findAllFilms(int page, int perPage) {

            List<Film> all = new ArrayList<>(films.values());

            int offset = Math.min(
                    (page - 1) * perPage,
                    all.size()
            );

            int end = Math.min(
                    offset + perPage,
                    all.size()
            );

            return new Page<>(
                    page,
                    perPage,
                    all.size(),
                    all.subList(offset, end)
            );
        }

        @Override
        public Film updateFilm(long filmId, Film film) {

            if (!films.containsKey(filmId)) {
                throw new FilmNotFoundException(filmId);
            }

            Film updated = new Film(
                    filmId,
                    film.title(),
                    film.year(),
                    film.description(),
                    CREATED_AT,
                    film.genres()
            );

            films.put(filmId, updated);

            return updated;
        }

        @Override
        public boolean deleteFilmById(long filmId) {
            return films.remove(filmId) != null;
        }

        @Override
        public Page<Film> findFilmsByTitle(String title, int page, int perPage) {
            return pageOf(
                    films.values().stream()
                            .filter(f -> f.title().toLowerCase()
                                    .contains(title.toLowerCase()))
                            .toList(),
                    page,
                    perPage
            );
        }

        @Override
        public Page<Film> findFilmsByYear(int year, int page, int perPage) {
            return pageOf(
                    films.values().stream()
                            .filter(f -> f.year() == year)
                            .toList(),
                    page,
                    perPage
            );
        }

        @Override
        public Page<Film> findFilmsByGenreId(long genreId, int page, int perPage) {
            return pageOf(
                    films.values().stream()
                            .filter(f -> f.genres().stream()
                                    .anyMatch(g -> g.genreId() == genreId))
                            .toList(),
                    page,
                    perPage
            );
        }

        // Mirrors the real repository: missing film or genre -> not found,
        // already assigned -> false, otherwise the association is created.
        @Override
        public boolean addGenreToFilm(long filmId, long genreId) {
            Film film = requireFilm(filmId);
            Genre genre = requireGenre(genreId);

            if (film.genres().stream().anyMatch(g -> g.genreId() == genreId)) {
                return false;
            }

            List<Genre> updated = new ArrayList<>(film.genres());
            updated.add(genre);
            films.put(filmId, film.withGenres(updated));

            return true;
        }

        // Missing film or genre -> not found, not assigned -> false.
        @Override
        public boolean removeGenreFromFilm(long filmId, long genreId) {
            Film film = requireFilm(filmId);
            requireGenre(genreId);

            List<Genre> updated = new ArrayList<>(film.genres());
            boolean removed = updated.removeIf(g -> g.genreId() == genreId);

            if (removed) {
                films.put(filmId, film.withGenres(updated));
            }

            return removed;
        }

        private Film requireFilm(long filmId) {
            Film film = films.get(filmId);

            if (film == null) {
                throw new FilmNotFoundException(filmId);
            }

            return film;
        }

        private Genre requireGenre(long genreId) {
            return genreRepository.findGenreById(genreId)
                    .orElseThrow(() -> new GenreNotFoundException(genreId));
        }

        private static Page<Film> pageOf(List<Film> all, int page, int perPage) {
            int from = Math.min(Page.offset(page, perPage), all.size());
            int to = Math.min(from + perPage, all.size());

            return new Page<>(page, perPage, all.size(), all.subList(from, to));
        }
    }

    // ---------------------------------------------------------
    // IN-MEMORY GENRE REPOSITORY
    // ---------------------------------------------------------

    private static class FakeGenreRepository
            implements GenreRepository {

        private final Map<Long, Genre> genres =
                new LinkedHashMap<>();

        private long nextId = 1;

        @Override
        public Genre createGenre(Genre genre) {

            if (findGenreByName(genre.name()).isPresent()) {
                throw new DuplicateGenreException(genre.name());
            }

            Genre persisted = new Genre(
                    nextId++,
                    genre.name()
            );

            genres.put(persisted.genreId(), persisted);

            return persisted;
        }

        @Override
        public Optional<Genre> findGenreById(long genreId) {
            return Optional.ofNullable(genres.get(genreId));
        }

        @Override
        public Optional<Genre> findGenreByName(String name) {
            return genres.values()
                    .stream()
                    .filter(genre -> genre.name().equals(name))
                    .findFirst();
        }

        @Override
        public List<Genre> findAllGenres() {
            return new ArrayList<>(genres.values());
        }

        @Override
        public Genre updateGenre(long genreId, Genre genre) {

            if (!genres.containsKey(genreId)) {
                throw new GenreNotFoundException(genreId);
            }

            Genre updated = new Genre(genreId, genre.name());

            genres.put(genreId, updated);

            return updated;
        }

        @Override
        public boolean deleteGenreById(long genreId) {
            return genres.remove(genreId) != null;
        }
    }
}