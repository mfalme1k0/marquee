
package com.marquee.service;

import com.marquee.exceptions.DuplicateGenreException;
import com.marquee.exceptions.FilmNotFoundException;
import com.marquee.exceptions.GenreNotFoundException;
import com.marquee.exceptions.ValidationException;
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

    private static final String CREATED_AT =
            "2026-11-18T10:00:00Z";

    @BeforeEach
    void setUp() {
        filmRepository = new FakeFilmRepository();
        genreRepository = new FakeGenreRepository();

        filmService = new FilmService(
                filmRepository,
                genreRepository
        );
    }

    // ---------------------------------------------------------
    // CONSTRUCTOR
    // ---------------------------------------------------------

    @Test
    void shouldCreateServiceWithValidRepositories() {
        assertNotNull(
                new FilmService(filmRepository, genreRepository)
        );
    }

    @Test
    void shouldRejectNullFilmRepository() {
        assertThrows(
                NullPointerException.class,
                () -> new FilmService(null, genreRepository)
        );
    }

    @Test
    void shouldRejectNullGenreRepository() {
        assertThrows(
                NullPointerException.class,
                () -> new FilmService(filmRepository, null)
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
    // IN-MEMORY FILM REPOSITORY
    // ---------------------------------------------------------

    private static class FakeFilmRepository
            implements FilmRepository {

        private final Map<Long, Film> films =
                new LinkedHashMap<>();

        private long nextId = 1;

        private Film lastCreatedFilm;

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
