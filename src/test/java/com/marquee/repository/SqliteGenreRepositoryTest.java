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

import org.sqlite.SQLiteErrorCode;
import org.sqlite.SQLiteException;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class SqliteGenreRepositoryTest {

    @TempDir
    Path tempDir;

    private GenreRepository genreRepository;

    private DatabaseManager databaseManager;

    @BeforeEach
    void setUp() throws Exception {
        databaseManager = new DatabaseManager(
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

    @Test
    void shouldHandleSuppressedExceptionsWhenResultSetAndStatementCloseFailDuringFindById() {
        GenreRepository repository = new SqliteGenreRepository(
                connectionProviderWithFailingQueryAndClose("resultSet"),
                new TransactionManager(databaseManager)
        );

        assertThrows(
                RepositoryException.class,
                () -> repository.findGenreById(1L)
        );
    }

    @Test
    void shouldHandleSuppressedExceptionsWhenResultSetAndStatementCloseFailDuringFindByName() {
        GenreRepository repository = new SqliteGenreRepository(
                connectionProviderWithFailingQueryAndClose("resultSet"),
                new TransactionManager(databaseManager)
        );

        assertThrows(
                RepositoryException.class,
                () -> repository.findGenreByName("Test Genre")
        );
    }

    @Test
    void shouldHandleSuppressedExceptionWhenStatementCloseFailsDuringFindById() {
        GenreRepository repository = new SqliteGenreRepository(
                connectionProviderWithFailingQueryAndClose("preparedStatement"),
                new TransactionManager(databaseManager)
        );

        assertThrows(
                RepositoryException.class,
                () -> repository.findGenreById(1L)
        );
    }

    @Test
    void shouldHandleSuppressedExceptionWhenStatementCloseFailsDuringFindByName() {
        GenreRepository repository = new SqliteGenreRepository(
                connectionProviderWithFailingQueryAndClose("preparedStatement"),
                new TransactionManager(databaseManager)
        );

        assertThrows(
                RepositoryException.class,
                () -> repository.findGenreByName("Test Genre")
        );
    }

    @Test
    void shouldThrowRepositoryExceptionWhenConnectionProviderIsNull() {
        RepositoryException exception = assertThrows(
                RepositoryException.class,
                () -> new SqliteGenreRepository(null, new TransactionManager(databaseManager))
        );

        assertEquals("Connection provider must not be null", exception.getMessage());
    }

    @Test
    void shouldThrowRepositoryExceptionWhenTransactionManagerIsNull() {
        RepositoryException exception = assertThrows(
                RepositoryException.class,
                () -> new SqliteGenreRepository(databaseManager, null)
        );

        assertEquals("Transaction manager must not be null", exception.getMessage());
    }

    @Test
    void shouldWrapSQLExceptionWhenCreatingGenreFailsForNonUniqueReason() {
        ConnectionProvider failingProvider =
                connectionProviderThrowingOnPrepareStatement(
                        new SQLException("Simulated database failure")
                );

        GenreRepository repository = new SqliteGenreRepository(
                failingProvider,
                new TransactionManager(failingProvider)
        );

        RepositoryException exception = assertThrows(
                RepositoryException.class,
                () -> repository.createGenre(new Genre(null, "Action"))
        );

        assertEquals("Failed to create genre", exception.getMessage());
        assertInstanceOf(SQLException.class, exception.getCause());
    }

    @Test
    void shouldWrapNonUniqueSQLiteExceptionWhenUpdatingGenre() {
        ConnectionProvider failingProvider =
                connectionProviderThrowingOnPrepareStatement(
                        new SQLiteException(
                                "Simulated non-unique constraint failure",
                                SQLiteErrorCode.SQLITE_ERROR
                        )
                );

        GenreRepository repository = new SqliteGenreRepository(
                failingProvider,
                new TransactionManager(databaseManager)
        );

        RepositoryException exception = assertThrows(
                RepositoryException.class,
                () -> repository.updateGenre(1L, new Genre(null, "Updated"))
        );

        assertEquals("Failed to update genre", exception.getMessage());
        assertInstanceOf(SQLiteException.class, exception.getCause());
    }

    @Test
    void shouldWrapSQLExceptionWhenConnectionProviderFails() {
        ConnectionProvider failingProvider = () -> {
            throw new SQLException("Simulated connection failure");
        };

        GenreRepository repository = new SqliteGenreRepository(
                failingProvider,
                new TransactionManager(databaseManager)
        );

        RepositoryException exception = assertThrows(
                RepositoryException.class,
                () -> repository.findGenreById(1L)
        );

        assertEquals("Database operation failed", exception.getMessage());
        assertInstanceOf(SQLException.class, exception.getCause());
    }

    @Test
    void shouldWrapSQLExceptionWhenResultSetCloseFailsDuringFindById() {
        Genre genre = genreRepository.createGenre(
                new Genre(null, "Close Test Genre"));

        GenreRepository repository = new SqliteGenreRepository(
                connectionProviderWithFailingClose("resultSet"),
                new TransactionManager(databaseManager)
        );

        RepositoryException exception = assertThrows(
                RepositoryException.class,
                () -> repository.findGenreById(genre.genreId())
        );

        assertEquals("Database operation failed", exception.getMessage());
    }

    @Test
    void shouldWrapSQLExceptionWhenPreparedStatementCloseFailsDuringFindById() {
        Genre genre = genreRepository.createGenre(
                new Genre(null, "Close Test Genre"));

        GenreRepository repository = new SqliteGenreRepository(
                connectionProviderWithFailingClose("preparedStatement"),
                new TransactionManager(databaseManager)
        );

        RepositoryException exception = assertThrows(
                RepositoryException.class,
                () -> repository.findGenreById(genre.genreId())
        );

        assertEquals("Database operation failed", exception.getMessage());
    }

    @Test
    void shouldWrapSQLExceptionWhenResultSetCloseFailsDuringFindByName() {
        Genre genre = genreRepository.createGenre(
                new Genre(null, "Close Test Genre"));

        GenreRepository repository = new SqliteGenreRepository(
                connectionProviderWithFailingClose("resultSet"),
                new TransactionManager(databaseManager)
        );

        RepositoryException exception = assertThrows(
                RepositoryException.class,
                () -> repository.findGenreByName(genre.name())
        );

        assertEquals("Database operation failed", exception.getMessage());
    }

    @Test
    void shouldWrapSQLExceptionWhenPreparedStatementCloseFailsDuringFindByName() {
        Genre genre = genreRepository.createGenre(
                new Genre(null, "Close Test Genre"));

        GenreRepository repository = new SqliteGenreRepository(
                connectionProviderWithFailingClose("preparedStatement"),
                new TransactionManager(databaseManager)
        );

        RepositoryException exception = assertThrows(
                RepositoryException.class,
                () -> repository.findGenreByName(genre.name())
        );

        assertEquals("Database operation failed", exception.getMessage());
    }

    private ConnectionProvider connectionProviderThrowingOnPrepareStatement(
            SQLException failure) {

        return () -> {
            Connection connection = databaseManager.getConnection();

            return (Connection) Proxy.newProxyInstance(
                    Connection.class.getClassLoader(),
                    new Class<?>[]{Connection.class},
                    (proxy, method, args) -> {
                        if (method.getName().equals("prepareStatement")) {
                            throw failure;
                        }

                        try {
                            return method.invoke(connection, args);
                        } catch (InvocationTargetException exception) {
                            throw exception.getCause();
                        }
                    }
            );
        };
    }

    private ConnectionProvider connectionProviderWithFailingClose(
            String resourceToFail) {

        return () -> {
            Connection connection = databaseManager.getConnection();

            return (Connection) Proxy.newProxyInstance(
                    Connection.class.getClassLoader(),
                    new Class<?>[]{Connection.class},
                    (proxy, method, args) -> {
                        if (method.getName().equals("prepareStatement")) {
                            PreparedStatement statement =
                                    (PreparedStatement) method.invoke(connection, args);

                            return Proxy.newProxyInstance(
                                    PreparedStatement.class.getClassLoader(),
                                    new Class<?>[]{PreparedStatement.class},
                                    (statementProxy, statementMethod, statementArgs) -> {

                                        if (statementMethod.getName().equals("close")
                                                && resourceToFail.equals("preparedStatement")) {
                                            statement.close();
                                            throw new SQLException(
                                                    "Simulated PreparedStatement close failure"
                                            );
                                        }

                                        if (statementMethod.getName().equals("executeQuery")) {
                                            ResultSet resultSet =
                                                    (ResultSet) statementMethod.invoke(
                                                            statement,
                                                            statementArgs
                                                    );

                                            return Proxy.newProxyInstance(
                                                    ResultSet.class.getClassLoader(),
                                                    new Class<?>[]{ResultSet.class},
                                                    (resultSetProxy, resultSetMethod, resultSetArgs) -> {

                                                        if (resultSetMethod.getName().equals("close")
                                                                && resourceToFail.equals("resultSet")) {
                                                            resultSet.close();
                                                            throw new SQLException(
                                                                    "Simulated ResultSet close failure"
                                                            );
                                                        }

                                                        try {
                                                            return resultSetMethod.invoke(
                                                                    resultSet,
                                                                    resultSetArgs
                                                            );
                                                        } catch (InvocationTargetException exception) {
                                                            throw exception.getCause();
                                                        }
                                                    }
                                            );
                                        }

                                        try {
                                            return statementMethod.invoke(
                                                    statement,
                                                    statementArgs
                                            );
                                        } catch (InvocationTargetException exception) {
                                            throw exception.getCause();
                                        }
                                    }
                            );
                        }

                        try {
                            return method.invoke(connection, args);
                        } catch (InvocationTargetException exception) {
                            throw exception.getCause();
                        }
                    }
            );
        };
    }

    private ConnectionProvider connectionProviderWithFailingQueryAndClose(
            String resourceToFail) {

        return () -> {
            Connection connection = databaseManager.getConnection();

            return (Connection) Proxy.newProxyInstance(
                    Connection.class.getClassLoader(),
                    new Class<?>[]{Connection.class},
                    (proxy, method, args) -> {
                        if (method.getName().equals("prepareStatement")) {
                            PreparedStatement statement =
                                    (PreparedStatement) method.invoke(connection, args);

                            return Proxy.newProxyInstance(
                                    PreparedStatement.class.getClassLoader(),
                                    new Class<?>[]{PreparedStatement.class},
                                    (statementProxy, statementMethod, statementArgs) -> {

                                        if (statementMethod.getName().equals("close")) {
                                            statement.close();

                                            if (resourceToFail.equals("preparedStatement")) {
                                                throw new SQLException(
                                                        "Simulated statement close failure"
                                                );
                                            }

                                            return null;
                                        }

                                        if (statementMethod.getName().equals("executeQuery")) {
                                            ResultSet resultSet =
                                                    (ResultSet) statementMethod.invoke(
                                                            statement,
                                                            statementArgs
                                                    );

                                            return Proxy.newProxyInstance(
                                                    ResultSet.class.getClassLoader(),
                                                    new Class<?>[]{ResultSet.class},
                                                    (resultSetProxy, resultSetMethod, resultSetArgs) -> {

                                                        if (resultSetMethod.getName().equals("next")) {
                                                            throw new SQLException(
                                                                    "Simulated result set failure"
                                                            );
                                                        }

                                                        if (resultSetMethod.getName().equals("close")) {
                                                            resultSet.close();

                                                            if (resourceToFail.equals("resultSet")) {
                                                                throw new SQLException(
                                                                        "Simulated result set close failure"
                                                                );
                                                            }

                                                            return null;
                                                        }

                                                        try {
                                                            return resultSetMethod.invoke(
                                                                    resultSet,
                                                                    resultSetArgs
                                                            );
                                                        } catch (InvocationTargetException exception) {
                                                            throw exception.getCause();
                                                        }
                                                    }
                                            );
                                        }

                                        try {
                                            return statementMethod.invoke(
                                                    statement,
                                                    statementArgs
                                            );
                                        } catch (InvocationTargetException exception) {
                                            throw exception.getCause();
                                        }
                                    }
                            );
                        }

                        try {
                            return method.invoke(connection, args);
                        } catch (InvocationTargetException exception) {
                            throw exception.getCause();
                        }
                    }
            );
        };
    }
}