
package com.marquee.service;

import com.marquee.exceptions.DuplicateGenreException;
import com.marquee.exceptions.GenreNotFoundException;
import com.marquee.exceptions.ValidationException;
import com.marquee.logging.RecordingLogger;
import com.marquee.model.Genre;
import com.marquee.repository.GenreRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class GenreServiceTest {

    private FakeGenreRepository repository;
    private GenreService service;
    private RecordingLogger logger;

    @BeforeEach
    void setUp() {
        repository = new FakeGenreRepository();
        logger = new RecordingLogger();
        service = new GenreService(repository, logger);
    }

    @Test
    void shouldRejectNullRepository() {
        assertThrows(
                NullPointerException.class,
                () -> new GenreService(null, logger)
        );
    }

    @Test
    void shouldCreateGenre() {
        Genre created = service.createGenre(genre(null, "Drama"));

        assertNotNull(created.genreId());
        assertEquals("Drama", created.name());
    }

    @Test
    void shouldTrimGenreNameWhenCreating() {
        Genre created = service.createGenre(genre(null, "  Drama  "));

        assertEquals("Drama", created.name());
    }

    @Test
    void shouldRejectNullGenre() {
        assertThrows(
                ValidationException.class,
                () -> service.createGenre(null)
        );
    }

    @Test
    void shouldRejectNullGenreName() {
        assertThrows(
                ValidationException.class,
                () -> service.createGenre(genre(null, null))
        );
    }

    @Test
    void shouldRejectBlankGenreName() {
        assertThrows(
                ValidationException.class,
                () -> service.createGenre(genre(null, "   "))
        );
    }

    @Test
    void shouldRejectGenreNameLongerThanMaximum() {
        String longName = "A".repeat(101);

        assertThrows(
                ValidationException.class,
                () -> service.createGenre(genre(null, longName))
        );
    }

    @Test
    void shouldAcceptGenreNameAtMaximumLength() {
        String name = "A".repeat(100);

        Genre created = service.createGenre(genre(null, name));

        assertEquals(name, created.name());
    }

    @Test
    void shouldPropagateDuplicateGenreExceptionWhenCreating() {
        service.createGenre(genre(null, "Drama"));

        assertThrows(
                DuplicateGenreException.class,
                () -> service.createGenre(genre(null, "Drama"))
        );
    }

    @Test
    void shouldFindGenreById() {
        Genre created = service.createGenre(genre(null, "Comedy"));

        Genre found = service.getGenreById(created.genreId());

        assertEquals(created, found);
    }

    @Test
    void shouldRejectZeroGenreIdWhenRetrieving() {
        assertThrows(
                ValidationException.class,
                () -> service.getGenreById(0)
        );
    }

    @Test
    void shouldRejectNegativeGenreIdWhenRetrieving() {
        assertThrows(
                ValidationException.class,
                () -> service.getGenreById(-1)
        );
    }

    @Test
    void shouldThrowWhenGenreDoesNotExist() {
        assertThrows(
                GenreNotFoundException.class,
                () -> service.getGenreById(999)
        );
    }

    @Test
    void shouldReturnAllGenres() {
        service.createGenre(genre(null, "Drama"));
        service.createGenre(genre(null, "Comedy"));

        List<Genre> genres = service.getAllGenres();

        assertEquals(2, genres.size());
    }

    @Test
    void shouldReturnEmptyListWhenNoGenresExist() {
        assertTrue(service.getAllGenres().isEmpty());
    }

    @Test
    void shouldUpdateGenre() {
        Genre created = service.createGenre(genre(null, "Drama"));

        Genre updated = service.updateGenre(
                created.genreId(),
                genre(null, "Thriller")
        );

        assertEquals(created.genreId(), updated.genreId());
        assertEquals("Thriller", updated.name());
    }

    @Test
    void shouldTrimGenreNameWhenUpdating() {
        Genre created = service.createGenre(genre(null, "Drama"));

        Genre updated = service.updateGenre(
                created.genreId(),
                genre(null, "  Thriller  ")
        );

        assertEquals("Thriller", updated.name());
    }

    @Test
    void shouldRejectInvalidIdWhenUpdating() {
        assertThrows(
                ValidationException.class,
                () -> service.updateGenre(0, genre(null, "Thriller"))
        );
    }

    @Test
    void shouldRejectNullGenreWhenUpdating() {
        Genre created = service.createGenre(genre(null, "Drama"));

        assertThrows(
                ValidationException.class,
                () -> service.updateGenre(created.genreId(), null)
        );
    }

    @Test
    void shouldRejectNullNameWhenUpdating() {
        Genre created = service.createGenre(genre(null, "Drama"));

        assertThrows(
                ValidationException.class,
                () -> service.updateGenre(
                        created.genreId(),
                        genre(null, null)
                )
        );
    }

    @Test
    void shouldRejectBlankNameWhenUpdating() {
        Genre created = service.createGenre(genre(null, "Drama"));

        assertThrows(
                ValidationException.class,
                () -> service.updateGenre(
                        created.genreId(),
                        genre(null, " ")
                )
        );
    }

    @Test
    void shouldRejectNameLongerThanMaximumWhenUpdating() {
        Genre created = service.createGenre(genre(null, "Drama"));

        assertThrows(
                ValidationException.class,
                () -> service.updateGenre(
                        created.genreId(),
                        genre(null, "A".repeat(101))
                )
        );
    }

    @Test
    void shouldThrowWhenUpdatingNonexistentGenre() {
        assertThrows(
                GenreNotFoundException.class,
                () -> service.updateGenre(999, genre(null, "Thriller"))
        );
    }

    @Test
    void shouldPropagateDuplicateExceptionWhenUpdating() {
        Genre drama = service.createGenre(genre(null, "Drama"));
        service.createGenre(genre(null, "Comedy"));

        assertThrows(
                DuplicateGenreException.class,
                () -> service.updateGenre(
                        drama.genreId(),
                        genre(null, "Comedy")
                )
        );
    }

    @Test
    void shouldDeleteGenre() {
        Genre created = service.createGenre(genre(null, "Drama"));

        assertTrue(service.deleteGenre(created.genreId()));
        assertTrue(repository.findGenreById(created.genreId()).isEmpty());
    }

    @Test
    void shouldRejectInvalidIdWhenDeleting() {
        assertThrows(
                ValidationException.class,
                () -> service.deleteGenre(0)
        );
    }

    @Test
    void shouldReturnFalseWhenDeletingNonexistentGenre() {
        assertFalse(service.deleteGenre(999));
    }

    // ---------------------------------------------------------
    // LOGGING
    // ---------------------------------------------------------

    @Test
    void shouldRejectNullLogger() {
        assertThrows(
                NullPointerException.class,
                () -> new GenreService(repository, null)
        );
    }

    @Test
    void shouldLogGenreCreated() {
        Genre created = service.createGenre(genre(null, "  Drama  "));

        RecordingLogger.Event event = logger.only("genre.created");
        assertEquals("INFO", event.level());
        assertNull(event.userId());
        assertEquals(created.genreId(), event.context().get("genre_id"));
        assertEquals("Drama", event.context().get("name"));
        assertEquals(List.of("genre.created"), logger.eventNames());
    }

    @Test
    void shouldLogWarningWhenGenreCreationFailsValidation() {
        assertThrows(ValidationException.class, () -> service.createGenre(genre(null, "  ")));

        RecordingLogger.Event event = logger.only("genre.create.rejected");
        assertEquals("WARN", event.level());
        assertEquals("validation_failed", event.context().get("reason"));
        assertNotNull(event.context().get("details"));
        assertEquals(List.of("genre.create.rejected"), logger.eventNames());
    }

    @Test
    void shouldLogWarningWhenCreatingNullGenre() {
        assertThrows(ValidationException.class, () -> service.createGenre(null));

        assertEquals("validation_failed", logger.only("genre.create.rejected").context().get("reason"));
    }

    @Test
    void shouldNotLogGenreCreatedWhenNameIsDuplicate() {
        service.createGenre(genre(null, "Drama"));
        logger.clear();

        assertThrows(
                DuplicateGenreException.class,
                () -> service.createGenre(genre(null, "Drama"))
        );

        assertFalse(logger.eventNames().contains("genre.created"));
    }

    @Test
    void shouldLogGenreUpdated() {
        Genre created = service.createGenre(genre(null, "Drama"));
        logger.clear();

        service.updateGenre(created.genreId(), genre(null, "  Comedy "));

        RecordingLogger.Event event = logger.only("genre.updated");
        assertEquals("INFO", event.level());
        assertEquals(created.genreId(), event.context().get("genre_id"));
        assertEquals("Comedy", event.context().get("name"));
    }

    @Test
    void shouldLogWarningWhenGenreUpdateFailsValidation() {
        Genre created = service.createGenre(genre(null, "Drama"));
        logger.clear();

        assertThrows(
                ValidationException.class,
                () -> service.updateGenre(created.genreId(), genre(null, ""))
        );

        RecordingLogger.Event event = logger.only("genre.update.rejected");
        assertEquals("WARN", event.level());
        assertEquals("validation_failed", event.context().get("reason"));
        assertEquals(created.genreId(), event.context().get("genre_id"));
    }

    @Test
    void shouldLogWarningWhenGenreUpdateHasInvalidId() {
        assertThrows(
                ValidationException.class,
                () -> service.updateGenre(0, genre(null, "Drama"))
        );

        assertEquals(0L, logger.only("genre.update.rejected").context().get("genre_id"));
    }

    @Test
    void shouldLogWarningWhenGenreIsNotFoundById() {
        assertThrows(GenreNotFoundException.class, () -> service.getGenreById(77));

        RecordingLogger.Event event = logger.only("genre.not_found");
        assertEquals("WARN", event.level());
        assertEquals(77L, event.context().get("genre_id"));
    }

    @Test
    void shouldNotLogWhenGenreIsFoundOrListed() {
        Genre created = service.createGenre(genre(null, "Drama"));
        logger.clear();

        service.getGenreById(created.genreId());
        service.getAllGenres();

        assertTrue(logger.isEmpty());
    }

    @Test
    void shouldLogGenreDeleted() {
        Genre created = service.createGenre(genre(null, "Drama"));
        logger.clear();

        assertTrue(service.deleteGenre(created.genreId()));

        RecordingLogger.Event event = logger.only("genre.deleted");
        assertEquals("INFO", event.level());
        assertEquals(created.genreId(), event.context().get("genre_id"));
        assertEquals(List.of("genre.deleted"), logger.eventNames());
    }

    @Test
    void shouldLogWarningWhenDeletingMissingGenre() {
        assertFalse(service.deleteGenre(999));

        RecordingLogger.Event event = logger.only("genre.delete.not_found");
        assertEquals("WARN", event.level());
        assertEquals(999L, event.context().get("genre_id"));
        assertEquals(List.of("genre.delete.not_found"), logger.eventNames());
    }

    private static Genre genre(Long id, String name) {
        return new Genre(id, name);
    }

    private static class FakeGenreRepository implements GenreRepository {

        private final List<Genre> genres = new ArrayList<>();
        private long nextId = 1;

        @Override
        public Genre createGenre(Genre genre) {
            boolean duplicate = genres.stream()
                    .anyMatch(existing ->
                            existing.name().equalsIgnoreCase(genre.name())
                    );

            if (duplicate) {
                throw new DuplicateGenreException(genre.name());
            }

            Genre created = new Genre(nextId++, genre.name());
            genres.add(created);

            return created;
        }

        @Override
        public Optional<Genre> findGenreById(long genreId) {
            return genres.stream()
                    .filter(existing -> existing.genreId() == genreId)
                    .findFirst();
        }

        @Override
        public Optional<Genre> findGenreByName(String name) {
            return genres.stream()
                    .filter(existing ->
                            existing.name().equalsIgnoreCase(name)
                    )
                    .findFirst();
        }

        @Override
        public List<Genre> findAllGenres() {
            return List.copyOf(genres);
        }

        @Override
        public Genre updateGenre(long genreId, Genre updatedGenre) {
            Genre existing = findGenreById(genreId)
                    .orElseThrow(() ->
                            new GenreNotFoundException(genreId)
                    );

            boolean duplicate = genres.stream()
                    .anyMatch(candidate ->
                            candidate.genreId() != genreId
                                    && candidate.name().equalsIgnoreCase(
                                    updatedGenre.name()
                            )
                    );

            if (duplicate) {
                throw new DuplicateGenreException(updatedGenre.name());
            }

            Genre updated = new Genre(
                    genreId,
                    updatedGenre.name()
            );

            genres.remove(existing);
            genres.add(updated);

            return updated;
        }

        @Override
        public boolean deleteGenreById(long genreId) {
            return genres.removeIf(
                    existing -> existing.genreId() == genreId
            );
        }
    }
}
