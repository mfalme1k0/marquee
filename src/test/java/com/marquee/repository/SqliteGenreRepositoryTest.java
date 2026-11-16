package com.marquee.repository;

import com.marquee.db.DatabaseInitializer;
import com.marquee.db.DatabaseManager;
import com.marquee.db.transactions.ConnectionProvider;
import com.marquee.db.transactions.TransactionManager;
import com.marquee.exceptions.DuplicateGenreException;
import com.marquee.exceptions.GenreNotFoundException;
import com.marquee.exceptions.RepositoryException;
import com.marquee.model.Genre;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class SqliteGenreRepositoryTest {

    @TempDir
    Path tempDir;

    private GenreRepository genreRepository;

    @BeforeEach
    void setUp() throws Exception {
        DatabaseManager databaseManager = new DatabaseManager(
                tempDir.resolve("marquee.db")
        );

        DatabaseInitializer databaseInitializer =
                new DatabaseInitializer(databaseManager);

        databaseInitializer.initialize();

        ConnectionProvider connectionProvider = databaseManager;

        TransactionManager transactionManager =
                new TransactionManager(connectionProvider);

        genreRepository = new SqliteGenreRepository(
                connectionProvider,
                transactionManager
        );
    }

    @Test
    void shouldCreateGenreAndReturnGeneratedId() {
        Genre genre = new Genre(null, "Test Genre");

        Genre createdGenre = genreRepository.createGenre(genre);

        assertNotNull(createdGenre.genreId());
        assertTrue(createdGenre.genreId() > 0);
        assertEquals("Test Genre", createdGenre.name());
    }

    @Test
    void shouldFindGenreById() {
        Genre createdGenre = genreRepository.createGenre(
                new Genre(null, "Test Genre")
        );

        Optional<Genre> result = genreRepository.findGenreById(
                createdGenre.genreId()
        );

        assertTrue(result.isPresent());
        assertEquals(createdGenre, result.get());
    }

    @Test
    void shouldReturnEmptyWhenGenreIdDoesNotExist() {
        Optional<Genre> result = genreRepository.findGenreById(-1L);

        assertTrue(result.isEmpty());
    }

    @Test
    void shouldFindGenreByName() {
        Genre createdGenre = genreRepository.createGenre(
                new Genre(null, "Test Genre")
        );

        Optional<Genre> result = genreRepository.findGenreByName(
                "Test Genre"
        );

        assertTrue(result.isPresent());
        assertEquals(createdGenre, result.get());
    }

    @Test
    void shouldReturnEmptyWhenGenreNameDoesNotExist() {
        Optional<Genre> result = genreRepository.findGenreByName(
                "Nonexistent Genre"
        );

        assertTrue(result.isEmpty());
    }

    @Test
    void shouldFindAllGenresInAlphabeticalOrder() {
        genreRepository.createGenre(new Genre(null, "AAA Test Genre"));
        genreRepository.createGenre(new Genre(null, "MMM Test Genre"));
        genreRepository.createGenre(new Genre(null, "ZZZ Test Genre"));

        List<Genre> genres = genreRepository.findAllGenres();

        List<String> genreNames = genres.stream()
                .map(Genre::name)
                .toList();

        List<String> sortedGenreNames = genreNames.stream()
                .sorted()
                .toList();

        assertEquals(sortedGenreNames, genreNames);

        assertTrue(genreNames.contains("AAA Test Genre"));
        assertTrue(genreNames.contains("MMM Test Genre"));
        assertTrue(genreNames.contains("ZZZ Test Genre"));
    }

    @Test
    void shouldReturnEmptyListWhenNoGenresExist() throws Exception {
        DatabaseManager emptyDatabaseManager = new DatabaseManager(
                tempDir.resolve("empty.db")
        );

        new DatabaseInitializer(emptyDatabaseManager).initialize();

        try (var connection = emptyDatabaseManager.getConnection();
             var statement = connection.createStatement()) {

            statement.executeUpdate("DELETE FROM genres");
        }

        ConnectionProvider connectionProvider = emptyDatabaseManager;

        GenreRepository emptyRepository = new SqliteGenreRepository(
                connectionProvider,
                new TransactionManager(connectionProvider)
        );

        assertTrue(emptyRepository.findAllGenres().isEmpty());
    }

    @Test
    void shouldUpdateGenreName() {
        Genre createdGenre = genreRepository.createGenre(
                new Genre(null, "Original Name")
        );

        Genre updatedGenre = genreRepository.updateGenre(
                createdGenre.genreId(),
                new Genre(null, "Updated Name")
        );

        assertEquals(createdGenre.genreId(), updatedGenre.genreId());
        assertEquals("Updated Name", updatedGenre.name());

        assertEquals(
                Optional.of(updatedGenre),
                genreRepository.findGenreById(createdGenre.genreId())
        );
    }

    @Test
    void shouldThrowGenreNotFoundExceptionWhenUpdatingNonexistentGenre() {
        assertThrows(
                GenreNotFoundException.class,
                () -> genreRepository.updateGenre(
                        -1L,
                        new Genre(null, "Updated Name")
                )
        );
    }

    @Test
    void shouldThrowDuplicateGenreExceptionWhenCreatingDuplicateGenre() {
        genreRepository.createGenre(new Genre(null, "Unique Test Genre"));

        assertThrows(
                DuplicateGenreException.class,
                () -> genreRepository.createGenre(
                        new Genre(null, "Unique Test Genre")
                )
        );
    }

    @Test
    void shouldThrowDuplicateGenreExceptionWhenUpdatingToExistingName() {
        Genre firstGenre = genreRepository.createGenre(
                new Genre(null, "First Test Genre")
        );

        Genre secondGenre = genreRepository.createGenre(
                new Genre(null, "Second Test Genre")
        );

        assertThrows(
                DuplicateGenreException.class,
                () -> genreRepository.updateGenre(
                        secondGenre.genreId(),
                        new Genre(null, firstGenre.name())
                )
        );
    }

    @Test
    void shouldDeleteExistingGenre() {
        Genre createdGenre = genreRepository.createGenre(
                new Genre(null, "Test Genre")
        );

        assertTrue(
                genreRepository.deleteGenreById(createdGenre.genreId())
        );

        assertTrue(
                genreRepository.findGenreById(createdGenre.genreId()).isEmpty()
        );
    }

    @Test
    void shouldReturnFalseWhenDeletingNonexistentGenre() {
        assertFalse(genreRepository.deleteGenreById(-1L));
    }

    @Test
    void shouldThrowRepositoryExceptionWhenCreatingNullGenre() {
        assertThrows(
                RepositoryException.class,
                () -> genreRepository.createGenre(null)
        );
    }

    @Test
    void shouldThrowRepositoryExceptionWhenUpdatingWithNullGenre() {
        assertThrows(
                RepositoryException.class,
                () -> genreRepository.updateGenre(1L, null)
        );
    }
}