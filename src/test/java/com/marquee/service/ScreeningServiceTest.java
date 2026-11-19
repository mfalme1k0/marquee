
package com.marquee.service;

import com.marquee.exceptions.FilmNotFoundException;
import com.marquee.exceptions.ScreeningNotFoundException;
import com.marquee.exceptions.ValidationException;
import com.marquee.model.Film;
import com.marquee.model.Page;
import com.marquee.model.Screening;
import com.marquee.repository.FilmRepository;
import com.marquee.repository.ScreeningRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class ScreeningServiceTest {

    private FakeScreeningRepository screeningRepository;
    private FakeFilmRepository filmRepository;
    private ScreeningService service;

    private static final String VALID_TIME = "2026-11-20T19:00:00Z";

    @BeforeEach
    void setUp() {
        screeningRepository = new FakeScreeningRepository();
        filmRepository = new FakeFilmRepository();

        filmRepository.createFilm(
                new Film(null, "The Matrix", 1999, null, null, List.of())
        );

        service = new ScreeningService(screeningRepository, filmRepository);
    }

    @Test
    void shouldRejectNullScreeningRepository() {
        assertThrows(
                NullPointerException.class,
                () -> new ScreeningService(null, filmRepository)
        );
    }

    @Test
    void shouldRejectNullFilmRepository() {
        assertThrows(
                NullPointerException.class,
                () -> new ScreeningService(screeningRepository, null)
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

    @Test
    void shouldReturnAllScreenings() {
        createValidScreening();
        createValidScreening();

        assertEquals(2, service.getAllScreenings().size());
    }

    @Test
    void shouldReturnEmptyListWhenNoScreeningsExist() {
        assertTrue(service.getAllScreenings().isEmpty());
    }

    @Test
    void shouldFindScreeningsByFilmId() {
        createValidScreening();

        List<Screening> results = service.getScreeningsByFilmId(1L);

        assertEquals(1, results.size());
        assertEquals(1L, results.getFirst().filmId());
    }

    @Test
    void shouldRejectZeroFilmIdWhenFiltering() {
        assertThrows(
                ValidationException.class,
                () -> service.getScreeningsByFilmId(0)
        );
    }

    @Test
    void shouldRejectNegativeFilmIdWhenFiltering() {
        assertThrows(
                ValidationException.class,
                () -> service.getScreeningsByFilmId(-1)
        );
    }

    @Test
    void shouldThrowWhenFilteringByUnknownFilm() {
        assertThrows(
                FilmNotFoundException.class,
                () -> service.getScreeningsByFilmId(999)
        );
    }

    @Test
    void shouldFindScreeningsByVenue() {
        createValidScreening();

        assertEquals(
                1,
                service.getScreeningsByVenue("Cinema One").size()
        );
    }

    @Test
    void shouldTrimVenueWhenFiltering() {
        createValidScreening();

        assertEquals(
                1,
                service.getScreeningsByVenue("  Cinema One  ").size()
        );
    }

    @Test
    void shouldRejectNullVenueWhenFiltering() {
        assertThrows(
                ValidationException.class,
                () -> service.getScreeningsByVenue(null)
        );
    }

    @Test
    void shouldRejectBlankVenueWhenFiltering() {
        assertThrows(
                ValidationException.class,
                () -> service.getScreeningsByVenue("  ")
        );
    }

    @Test
    void shouldRejectVenueLongerThanMaximumWhenFiltering() {
        assertThrows(
                ValidationException.class,
                () -> service.getScreeningsByVenue("A".repeat(256))
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
        public List<Screening> findAllScreenings() {
            return List.copyOf(screenings);
        }

        @Override
        public List<Screening> findScreeningsByFilmId(long filmId) {
            return screenings.stream()
                    .filter(s -> s.filmId() == filmId)
                    .toList();
        }

        @Override
        public List<Screening> findScreeningsByVenue(String venue) {
            return screenings.stream()
                    .filter(s -> s.venue().equalsIgnoreCase(venue))
                    .toList();
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
    }
}
