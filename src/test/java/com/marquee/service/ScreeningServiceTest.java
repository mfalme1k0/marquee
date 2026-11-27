package com.marquee.service;

import com.marquee.exceptions.FilmNotFoundException;
import com.marquee.exceptions.ScreeningNotFoundException;
import com.marquee.exceptions.ValidationException;
import com.marquee.logging.RecordingLogger;
import com.marquee.model.Film;
import com.marquee.model.Page;
import com.marquee.model.Screening;
import com.marquee.repository.FilmRepository;
import com.marquee.repository.ScreeningRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class ScreeningServiceTest {

    private FakeScreeningRepository screeningRepository;
    private FakeFilmRepository filmRepository;
    private ScreeningService service;
    private RecordingLogger logger;

    private static final String VALID_TIME = "2026-11-20T19:00:00Z";

    @BeforeEach
    void setUp() {
        logger = new RecordingLogger();
        screeningRepository = new FakeScreeningRepository();
        filmRepository = new FakeFilmRepository();

        filmRepository.createFilm(
                new Film(null, "The Matrix", 1999, null, null, List.of())
        );

        service = new ScreeningService(screeningRepository, filmRepository, logger);
    }

    @Test
    void shouldRejectNullScreeningRepository() {
        assertThrows(
                NullPointerException.class,
                () -> new ScreeningService(null, filmRepository, logger)
        );
    }

    @Test
    void shouldRejectNullFilmRepository() {
        assertThrows(
                NullPointerException.class,
                () -> new ScreeningService(screeningRepository, null, logger)
        );
    }

    @Test
    void shouldCreateScreening() {
        Screening created = service.createScreening(
                screening(null, 1L, "Cinema One", VALID_TIME)
        );

        assertNotNull(created.screeningId());
        assertEquals(1L, created.filmId());
        assertEquals("Cinema One", created.venue());
    }

    @Test
    void shouldPassNullFilmTitleToRepository() {
        service.createScreening(
                screening(null, 1L, "Cinema One", VALID_TIME)
        );

        assertNull(screeningRepository.lastCreated.filmTitle());
    }

    @Test
    void shouldTrimVenueWhenCreating() {
        Screening created = service.createScreening(
                screening(null, 1L, "  Cinema One  ", VALID_TIME)
        );

        assertEquals("Cinema One", created.venue());
    }

    @Test
    void shouldNormalizeScreeningTimeWhenCreating() {
        Screening created = service.createScreening(
                screening(null, 1L, "Cinema One", "2026-11-20T19:00:00.000Z")
        );

        assertEquals(VALID_TIME, created.screeningTime());
    }

    @Test
    void shouldRejectNullScreening() {
        assertThrows(
                ValidationException.class,
                () -> service.createScreening(null)
        );
    }

    @Test
    void shouldRejectNullFilmId() {
        assertThrows(
                ValidationException.class,
                () -> service.createScreening(
                        screening(null, null, "Cinema One", VALID_TIME)
                )
        );
    }

    @Test
    void shouldRejectZeroFilmId() {
        assertThrows(
                ValidationException.class,
                () -> service.createScreening(
                        screening(null, 0L, "Cinema One", VALID_TIME)
                )
        );
    }

    @Test
    void shouldRejectNegativeFilmId() {
        assertThrows(
                ValidationException.class,
                () -> service.createScreening(
                        screening(null, -1L, "Cinema One", VALID_TIME)
                )
        );
    }

    @Test
    void shouldRejectUnknownFilmWhenCreating() {
        assertThrows(
                FilmNotFoundException.class,
                () -> service.createScreening(
                        screening(null, 999L, "Cinema One", VALID_TIME)
                )
        );
    }

    @Test
    void shouldRejectNullVenue() {
        assertThrows(
                ValidationException.class,
                () -> service.createScreening(
                        screening(null, 1L, null, VALID_TIME)
                )
        );
    }

    @Test
    void shouldRejectBlankVenue() {
        assertThrows(
                ValidationException.class,
                () -> service.createScreening(
                        screening(null, 1L, "   ", VALID_TIME)
                )
        );
    }

    @Test
    void shouldRejectVenueLongerThanMaximum() {
        assertThrows(
                ValidationException.class,
                () -> service.createScreening(
                        screening(null, 1L, "A".repeat(256), VALID_TIME)
                )
        );
    }

    @Test
    void shouldAcceptVenueAtMaximumLength() {
        String venue = "A".repeat(255);

        Screening created = service.createScreening(
                screening(null, 1L, venue, VALID_TIME)
        );

        assertEquals(venue, created.venue());
    }

    @Test
    void shouldRejectNullScreeningTime() {
        assertThrows(
                ValidationException.class,
                () -> service.createScreening(
                        screening(null, 1L, "Cinema One", null)
                )
        );
    }

    @Test
    void shouldRejectBlankScreeningTime() {
        assertThrows(
                ValidationException.class,
                () -> service.createScreening(
                        screening(null, 1L, "Cinema One", " ")
                )
        );
    }

    @Test
    void shouldRejectMalformedScreeningTime() {
        assertThrows(
                ValidationException.class,
                () -> service.createScreening(
                        screening(null, 1L, "Cinema One", "not-a-date")
                )
        );
    }

    @Test
    void shouldRejectTimestampWithoutUtcSuffix() {
        assertThrows(
                ValidationException.class,
                () -> service.createScreening(
                        screening(null, 1L, "Cinema One", "2026-11-20T19:00:00")
                )
        );
    }

    @Test
    void shouldFindScreeningById() {
        Screening created = createValidScreening();

        Screening found = service.getScreeningById(created.screeningId());

        assertEquals(created, found);
    }

    @Test
    void shouldRejectZeroScreeningIdWhenRetrieving() {
        assertThrows(
                ValidationException.class,
                () -> service.getScreeningById(0)
        );
    }

    @Test
    void shouldRejectNegativeScreeningIdWhenRetrieving() {
        assertThrows(
                ValidationException.class,
                () -> service.getScreeningById(-1)
        );
    }

    @Test
    void shouldThrowWhenScreeningDoesNotExist() {
        assertThrows(
                ScreeningNotFoundException.class,
                () -> service.getScreeningById(999)
        );
    }

    // ---- list all (paginated) ---------------------------------------------

    @Test
    void shouldReturnAllScreenings() {
        createValidScreening();
        createValidScreening();

        Page<Screening> result = service.getAllScreenings(1, 20);

        assertEquals(2, result.items().size());
        assertEquals(2, result.total());
        assertEquals(1, result.page());
        assertEquals(20, result.perPage());
    }

    @Test
    void shouldReturnEmptyPageWhenNoScreeningsExist() {
        Page<Screening> result = service.getAllScreenings(1, 20);

        assertTrue(result.items().isEmpty());
        assertEquals(0, result.total());
    }

    @Test
    void shouldReturnRequestedPageOfScreenings() {
        createValidScreening();
        createValidScreening();
        createValidScreening();

        Page<Screening> second = service.getAllScreenings(2, 2);

        assertEquals(1, second.items().size());
        assertEquals(3, second.total());
        assertEquals(2, second.page());
    }

    @Test
    void shouldReturnEmptyItemsWhenPageIsBeyondLastPage() {
        createValidScreening();

        Page<Screening> result = service.getAllScreenings(5, 20);

        assertTrue(result.items().isEmpty());
        assertEquals(1, result.total());
    }

    @Test
    void shouldRejectZeroPage() {
        assertThrows(
                ValidationException.class,
                () -> service.getAllScreenings(0, 20)
        );
    }

    @Test
    void shouldRejectNegativePage() {
        assertThrows(
                ValidationException.class,
                () -> service.getAllScreenings(-1, 20)
        );
    }

    @Test
    void shouldRejectZeroPerPage() {
        assertThrows(
                ValidationException.class,
                () -> service.getAllScreenings(1, 0)
        );
    }

    @Test
    void shouldRejectPerPageAboveMaximum() {
        assertThrows(
                ValidationException.class,
                () -> service.getAllScreenings(1, 101)
        );
    }

    @Test
    void shouldAcceptPerPageAtMaximum() {
        assertEquals(100, service.getAllScreenings(1, 100).perPage());
    }

    @Test
    void shouldReportEveryPaginationProblemTogether() {
        ValidationException exception = assertThrows(
                ValidationException.class,
                () -> service.getAllScreenings(0, 0)
        );

        assertEquals(2, exception.getDetails().size());
    }

    // ---- filter by film ---------------------------------------------------

    @Test
    void shouldFindScreeningsByFilmId() {
        createValidScreening();

        Page<Screening> results = service.getScreeningsByFilmId(1L, 1, 20);

        assertEquals(1, results.items().size());
        assertEquals(1L, results.items().getFirst().filmId());
        assertEquals(1, results.total());
    }

    @Test
    void shouldReturnEmptyPageWhenFilmHasNoScreenings() {
        Page<Screening> results = service.getScreeningsByFilmId(1L, 1, 20);

        assertTrue(results.items().isEmpty());
        assertEquals(0, results.total());
    }

    @Test
    void shouldRejectZeroFilmIdWhenFiltering() {
        assertThrows(
                ValidationException.class,
                () -> service.getScreeningsByFilmId(0, 1, 20)
        );
    }

    @Test
    void shouldRejectNegativeFilmIdWhenFiltering() {
        assertThrows(
                ValidationException.class,
                () -> service.getScreeningsByFilmId(-1, 1, 20)
        );
    }

    @Test
    void shouldThrowWhenFilteringByUnknownFilm() {
        assertThrows(
                FilmNotFoundException.class,
                () -> service.getScreeningsByFilmId(999, 1, 20)
        );
    }

    @Test
    void shouldRejectInvalidPaginationWhenFilteringByFilm() {
        assertThrows(
                ValidationException.class,
                () -> service.getScreeningsByFilmId(1L, 0, 20)
        );
    }

    // ---- filter by venue --------------------------------------------------

    @Test
    void shouldFindScreeningsByVenue() {
        createValidScreening();

        assertEquals(
                1,
                service.getScreeningsByVenue("Cinema One", 1, 20).items().size()
        );
    }

    @Test
    void shouldTrimVenueWhenFiltering() {
        createValidScreening();

        assertEquals(
                1,
                service.getScreeningsByVenue("  Cinema One  ", 1, 20)
                        .items().size()
        );
    }

    @Test
    void shouldReturnEmptyPageWhenNoScreeningMatchesVenue() {
        createValidScreening();

        Page<Screening> results =
                service.getScreeningsByVenue("Rooftop", 1, 20);

        assertTrue(results.items().isEmpty());
        assertEquals(0, results.total());
    }

    @Test
    void shouldRejectNullVenueWhenFiltering() {
        assertThrows(
                ValidationException.class,
                () -> service.getScreeningsByVenue(null, 1, 20)
        );
    }

    @Test
    void shouldRejectBlankVenueWhenFiltering() {
        assertThrows(
                ValidationException.class,
                () -> service.getScreeningsByVenue("  ", 1, 20)
        );
    }

    @Test
    void shouldRejectVenueLongerThanMaximumWhenFiltering() {
        assertThrows(
                ValidationException.class,
                () -> service.getScreeningsByVenue("A".repeat(256), 1, 20)
        );
    }

    @Test
    void shouldRejectInvalidPaginationWhenFilteringByVenue() {
        assertThrows(
                ValidationException.class,
                () -> service.getScreeningsByVenue("Cinema One", 1, 0)
        );
    }

    // ---- filter by date ---------------------------------------------------

    @Test
    void shouldFindScreeningsByDate() {
        createValidScreening();
        service.createScreening(
                screening(null, 1L, "Cinema One", "2026-11-21T19:00:00Z")
        );

        Page<Screening> results =
                service.getScreeningsByDate("2026-11-20", 1, 20);

        assertEquals(1, results.items().size());
        assertEquals(VALID_TIME, results.items().getFirst().screeningTime());
        assertEquals(1, results.total());
    }

    @Test
    void shouldTrimDateWhenFiltering() {
        createValidScreening();

        assertEquals(
                1,
                service.getScreeningsByDate("  2026-11-20  ", 1, 20)
                        .items().size()
        );
    }

    @Test
    void shouldReturnEmptyPageWhenNoScreeningOnDate() {
        createValidScreening();

        Page<Screening> results =
                service.getScreeningsByDate("2030-01-01", 1, 20);

        assertTrue(results.items().isEmpty());
        assertEquals(0, results.total());
    }

    @Test
    void shouldRejectNullDate() {
        assertThrows(
                ValidationException.class,
                () -> service.getScreeningsByDate(null, 1, 20)
        );
    }

    @Test
    void shouldRejectBlankDate() {
        assertThrows(
                ValidationException.class,
                () -> service.getScreeningsByDate("  ", 1, 20)
        );
    }

    @Test
    void shouldRejectMalformedDate() {
        assertThrows(
                ValidationException.class,
                () -> service.getScreeningsByDate("20/11/2026", 1, 20)
        );
    }

    @Test
    void shouldRejectImpossibleCalendarDate() {
        assertThrows(
                ValidationException.class,
                () -> service.getScreeningsByDate("2026-13-45", 1, 20)
        );
    }

    @Test
    void shouldRejectInvalidPaginationWhenFilteringByDate() {
        assertThrows(
                ValidationException.class,
                () -> service.getScreeningsByDate("2026-11-20", 0, 20)
        );
    }

    @Test
    void shouldUpdateScreening() {
        Screening created = createValidScreening();

        Screening updated = service.updateScreening(
                created.screeningId(),
                screening(null, 1L, "Cinema Two", VALID_TIME)
        );

        assertEquals(created.screeningId(), updated.screeningId());
        assertEquals("Cinema Two", updated.venue());
    }

    @Test
    void shouldPassNullFilmTitleWhenUpdating() {
        Screening created = createValidScreening();

        service.updateScreening(
                created.screeningId(),
                screening(null, 1L, "Cinema Two", VALID_TIME)
        );

        assertNull(screeningRepository.lastUpdated.filmTitle());
    }

    @Test
    void shouldRejectInvalidIdWhenUpdating() {
        assertThrows(
                ValidationException.class,
                () -> service.updateScreening(
                        0,
                        screening(null, 1L, "Cinema Two", VALID_TIME)
                )
        );
    }

    @Test
    void shouldThrowWhenUpdatingUnknownScreening() {
        assertThrows(
                ScreeningNotFoundException.class,
                () -> service.updateScreening(
                        999,
                        screening(null, 1L, "Cinema Two", VALID_TIME)
                )
        );
    }

    @Test
    void shouldRejectNullScreeningWhenUpdating() {
        Screening created = createValidScreening();

        assertThrows(
                ValidationException.class,
                () -> service.updateScreening(created.screeningId(), null)
        );
    }

    @Test
    void shouldRejectUnknownFilmWhenUpdating() {
        Screening created = createValidScreening();

        assertThrows(
                FilmNotFoundException.class,
                () -> service.updateScreening(
                        created.screeningId(),
                        screening(null, 999L, "Cinema Two", VALID_TIME)
                )
        );
    }

    @Test
    void shouldRejectInvalidVenueWhenUpdating() {
        Screening created = createValidScreening();

        assertThrows(
                ValidationException.class,
                () -> service.updateScreening(
                        created.screeningId(),
                        screening(null, 1L, " ", VALID_TIME)
                )
        );
    }

    @Test
    void shouldRejectInvalidTimestampWhenUpdating() {
        Screening created = createValidScreening();

        assertThrows(
                ValidationException.class,
                () -> service.updateScreening(
                        created.screeningId(),
                        screening(null, 1L, "Cinema Two", "invalid")
                )
        );
    }

    @Test
    void shouldDeleteScreening() {
        Screening created = createValidScreening();

        assertTrue(service.deleteScreening(created.screeningId()));
        assertTrue(
                screeningRepository.findScreeningById(created.screeningId()).isEmpty()
        );
    }

    @Test
    void shouldRejectInvalidIdWhenDeleting() {
        assertThrows(
                ValidationException.class,
                () -> service.deleteScreening(0)
        );
    }

    @Test
    void shouldReturnFalseWhenDeletingUnknownScreening() {
        assertFalse(service.deleteScreening(999));
    }

    // ---------------------------------------------------------
    // LOGGING
    // ---------------------------------------------------------

    @Test
    void shouldRejectNullLogger() {
        assertThrows(
                NullPointerException.class,
                () -> new ScreeningService(screeningRepository, filmRepository, null)
        );
    }

    @Test
    void shouldLogScreeningCreated() {
        Screening created = service.createScreening(
                screening(null, 1L, "  Cinema One ", VALID_TIME)
        );

        RecordingLogger.Event event = logger.only("screening.created");
        assertEquals("INFO", event.level());
        assertNull(event.userId());
        assertEquals(created.screeningId(), event.context().get("screening_id"));
        assertEquals(1L, event.context().get("film_id"));
        assertEquals("Cinema One", event.context().get("venue"));
        assertEquals(List.of("screening.created"), logger.eventNames());
    }

    @Test
    void shouldLogWarningWhenScreeningCreationFailsValidation() {
        assertThrows(
                ValidationException.class,
                () -> service.createScreening(screening(null, 1L, " ", VALID_TIME))
        );

        RecordingLogger.Event event = logger.only("screening.create.rejected");
        assertEquals("WARN", event.level());
        assertEquals("ValidationException", event.context().get("reason"));
        assertEquals("1", event.context().get("film_id"));
        assertEquals(List.of("screening.create.rejected"), logger.eventNames());
    }

    @Test
    void shouldLogWarningWhenCreatingNullScreening() {
        assertThrows(ValidationException.class, () -> service.createScreening(null));

        assertEquals("null", logger.only("screening.create.rejected").context().get("film_id"));
    }

    @Test
    void shouldLogWarningWhenScreeningCreationReferencesMissingFilm() {
        assertThrows(
                FilmNotFoundException.class,
                () -> service.createScreening(screening(null, 999L, "Cinema One", VALID_TIME))
        );

        RecordingLogger.Event event = logger.only("screening.create.rejected");
        assertEquals("FilmNotFoundException", event.context().get("reason"));
        assertEquals("999", event.context().get("film_id"));
    }

    @Test
    void shouldLogScreeningUpdated() {
        Screening created = createValidScreening();
        logger.clear();

        service.updateScreening(
                created.screeningId(),
                screening(null, 1L, "Cinema Two", VALID_TIME)
        );

        RecordingLogger.Event event = logger.only("screening.updated");
        assertEquals("INFO", event.level());
        assertEquals(created.screeningId(), event.context().get("screening_id"));
        assertEquals(1L, event.context().get("film_id"));
    }

    @Test
    void shouldLogWarningWhenScreeningUpdateFailsValidation() {
        Screening created = createValidScreening();
        logger.clear();

        assertThrows(
                ValidationException.class,
                () -> service.updateScreening(
                        created.screeningId(),
                        screening(null, 1L, "Cinema Two", "not-a-time")
                )
        );

        RecordingLogger.Event event = logger.only("screening.update.rejected");
        assertEquals("WARN", event.level());
        assertEquals("ValidationException", event.context().get("reason"));
        assertEquals(created.screeningId(), event.context().get("screening_id"));
        assertEquals("1", event.context().get("film_id"));
    }

    @Test
    void shouldLogWarningWhenScreeningUpdateReferencesMissingFilm() {
        Screening created = createValidScreening();
        logger.clear();

        assertThrows(
                FilmNotFoundException.class,
                () -> service.updateScreening(
                        created.screeningId(),
                        screening(null, 999L, "Cinema Two", VALID_TIME)
                )
        );

        assertEquals(
                "FilmNotFoundException",
                logger.only("screening.update.rejected").context().get("reason")
        );
    }

    @Test
    void shouldLogWarningWhenScreeningIsNotFoundById() {
        assertThrows(ScreeningNotFoundException.class, () -> service.getScreeningById(55));

        RecordingLogger.Event event = logger.only("screening.not_found");
        assertEquals("WARN", event.level());
        assertEquals(55L, event.context().get("screening_id"));
    }

    @Test
    void shouldLogWarningWhenListingScreeningsForMissingFilm() {
        assertThrows(
                FilmNotFoundException.class,
                () -> service.getScreeningsByFilmId(999, 1, 10)
        );

        RecordingLogger.Event event = logger.only("screening.list_by_film.rejected");
        assertEquals("WARN", event.level());
        assertEquals("film_not_found", event.context().get("reason"));
        assertEquals(999L, event.context().get("film_id"));
    }

    @Test
    void shouldNotLogWhenScreeningsAreReadSuccessfully() {
        Screening created = createValidScreening();
        logger.clear();

        service.getScreeningById(created.screeningId());
        service.getScreeningsByFilmId(1, 1, 10);
        service.getAllScreenings(1, 10);

        assertTrue(logger.isEmpty());
    }

    @Test
    void shouldLogScreeningDeleted() {
        Screening created = createValidScreening();
        logger.clear();

        assertTrue(service.deleteScreening(created.screeningId()));

        RecordingLogger.Event event = logger.only("screening.deleted");
        assertEquals("INFO", event.level());
        assertEquals(created.screeningId(), event.context().get("screening_id"));
        assertEquals(List.of("screening.deleted"), logger.eventNames());
    }

    @Test
    void shouldLogWarningWhenDeletingMissingScreening() {
        assertFalse(service.deleteScreening(999));

        RecordingLogger.Event event = logger.only("screening.delete.not_found");
        assertEquals("WARN", event.level());
        assertEquals(999L, event.context().get("screening_id"));
        assertEquals(List.of("screening.delete.not_found"), logger.eventNames());
    }

    private Screening createValidScreening() {
        return service.createScreening(
                screening(null, 1L, "Cinema One", VALID_TIME)
        );
    }

    private static Screening screening(
            Long id,
            Long filmId,
            String venue,
            String screeningTime
    ) {
        return new Screening(id, filmId, null, venue, screeningTime);
    }

    private static class FakeScreeningRepository
            implements ScreeningRepository {

        private final List<Screening> screenings = new ArrayList<>();
        private long nextId = 1;

        private Screening lastCreated;
        private Screening lastUpdated;

        @Override
        public Screening createScreening(Screening screening) {
            Screening created = new Screening(
                    nextId++,
                    screening.filmId(),
                    screening.filmTitle(),
                    screening.venue(),
                    screening.screeningTime()
            );

            screenings.add(created);
            lastCreated = created;

            return created;
        }

        @Override
        public Optional<Screening> findScreeningById(long screeningId) {
            return screenings.stream()
                    .filter(s -> s.screeningId() == screeningId)
                    .findFirst();
        }

        @Override
        public Page<Screening> findAllScreenings(int page, int perPage) {
            return pageOf(screenings, page, perPage);
        }

        @Override
        public Page<Screening> findScreeningsByFilmId(
                long filmId,
                int page,
                int perPage
        ) {
            return pageOf(
                    screenings.stream()
                            .filter(s -> s.filmId() == filmId)
                            .toList(),
                    page,
                    perPage
            );
        }

        @Override
        public Page<Screening> findScreeningsByVenue(
                String venue,
                int page,
                int perPage
        ) {
            return pageOf(
                    screenings.stream()
                            .filter(s -> s.venue().equalsIgnoreCase(venue))
                            .toList(),
                    page,
                    perPage
            );
        }

        @Override
        public Page<Screening> findScreeningsByDate(
                LocalDate date,
                int page,
                int perPage
        ) {
            return pageOf(
                    screenings.stream()
                            .filter(s -> s.screeningTime()
                                    .startsWith(date.toString()))
                            .toList(),
                    page,
                    perPage
            );
        }

        private static <T> Page<T> pageOf(
                List<T> all,
                int page,
                int perPage
        ) {
            int from = Math.min(Page.offset(page, perPage), all.size());
            int to = Math.min(from + perPage, all.size());

            return new Page<>(page, perPage, all.size(), all.subList(from, to));
        }

        @Override
        public Screening updateScreening(
                long screeningId,
                Screening updatedScreening
        ) {
            Screening existing = findScreeningById(screeningId)
                    .orElseThrow(() ->
                            new ScreeningNotFoundException(screeningId)
                    );

            Screening updated = new Screening(
                    screeningId,
                    updatedScreening.filmId(),
                    updatedScreening.filmTitle(),
                    updatedScreening.venue(),
                    updatedScreening.screeningTime()
            );

            screenings.remove(existing);
            screenings.add(updated);
            lastUpdated = updated;

            return updated;
        }

        @Override
        public boolean deleteScreeningById(long screeningId) {
            return screenings.removeIf(
                    s -> s.screeningId() == screeningId
            );
        }
    }

    private static class FakeFilmRepository implements FilmRepository {

        private final List<Film> films = new ArrayList<>();
        private long nextId = 1;

        @Override
        public Film createFilm(Film film) {
            Film created = new Film(
                    nextId++,
                    film.title(),
                    film.year(),
                    film.description(),
                    film.createdAt(),
                    film.genres()
            );

            films.add(created);
            return created;
        }

        @Override
        public Optional<Film> findFilmById(long filmId) {
            return films.stream()
                    .filter(f -> f.filmId() == filmId)
                    .findFirst();
        }

        @Override
        public Page<Film> findAllFilms(int page, int perPage) {
            return new Page<>(page, perPage, films.size(), films);
        }

        @Override
        public Film updateFilm(long filmId, Film film) {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean deleteFilmById(long filmId) {
            return films.removeIf(f -> f.filmId() == filmId);
        }

        // Not used by ScreeningService; present to satisfy the interface.

        @Override
        public Page<Film> findFilmsByTitle(String title, int page, int perPage) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Page<Film> findFilmsByYear(int year, int page, int perPage) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Page<Film> findFilmsByGenreId(long genreId, int page, int perPage) {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean addGenreToFilm(long filmId, long genreId) {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean removeGenreFromFilm(long filmId, long genreId) {
            throw new UnsupportedOperationException();
        }
    }
}