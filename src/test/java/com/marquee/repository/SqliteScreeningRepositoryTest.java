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

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.nio.file.Path;
import java.sql.*;
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

        TransactionManager transactionManager =
                new TransactionManager(databaseManager);

        screeningRepository = new SqliteScreeningRepository(
                databaseManager,
                transactionManager
        );

        filmRepository = new SqliteFilmRepository(
                databaseManager,
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
    void shouldThrowRepositoryExceptionWhenGeneratedScreeningIdCannotBeRetrieved()
            throws SQLException {

        ConnectionProvider provider = connectionProvider(
                "SELECT last_insert_rowid()",
                true,
                ResourceToFail.NONE
        );

        ScreeningRepository repository = new SqliteScreeningRepository(
                provider,
                new TransactionManager(provider)
        );

        Film film = createFilm("Test Film");

        RepositoryException exception = assertThrows(
                RepositoryException.class,
                () -> repository.createScreening(
                        new Screening(
                                null,
                                film.filmId(),
                                null,
                                "Test Cinema",
                                "2026-12-01T18:00:00"
                        )
                )
        );

        assertEquals(
                "Failed to retrieve the generated screening ID",
                exception.getMessage()
        );

        assertEquals(0, countScreenings());
    }

    @Test
    void shouldThrowScreeningNotFoundExceptionWhenCreatedScreeningCannotBeRetrieved()
            throws SQLException {

        ConnectionProvider provider = connectionProvider(
                "FROM screenings s",
                true,
                ResourceToFail.NONE
        );

        ScreeningRepository repository = new SqliteScreeningRepository(
                provider,
                new TransactionManager(provider)
        );

        Film film = createFilm("Test Film");

        assertThrows(
                ScreeningNotFoundException.class,
                () -> repository.createScreening(
                        new Screening(
                                null,
                                film.filmId(),
                                null,
                                "Test Cinema",
                                "2026-12-01T18:00:00"
                        )
                )
        );

        assertEquals(0, countScreenings());
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
    void shouldThrowRepositoryExceptionWhenCreatingScreeningFailsWithSQLException() throws SQLException {
        Film film = createFilm("Test Film");

        ConnectionProvider provider =
                connectionProviderThrowingSQLException("INSERT INTO screenings");

        ScreeningRepository repository = new SqliteScreeningRepository(
                provider,
                new TransactionManager(provider)
        );

        RepositoryException exception = assertThrows(
                RepositoryException.class,
                () -> repository.createScreening(
                        new Screening(
                                null,
                                film.filmId(),
                                null,
                                "Test Cinema",
                                "2026-12-01T18:00:00"
                        )
                )
        );

        assertEquals("Failed to create screening", exception.getMessage());
        assertInstanceOf(SQLException.class, exception.getCause());
        assertEquals(0, countScreenings());
    }

    @Test
    void shouldThrowRepositoryExceptionWhenUpdatingScreeningFailsWithSQLException() {
        Screening createdScreening = createScreening(
                "Test Film",
                "Test Cinema",
                "2026-12-01T18:00:00"
        );

        Film replacementFilm = createFilm("Replacement Film");

        ConnectionProvider provider =
                connectionProviderThrowingSQLException("UPDATE screenings");

        ScreeningRepository repository = new SqliteScreeningRepository(
                provider,
                new TransactionManager(provider)
        );

        RepositoryException exception = assertThrows(
                RepositoryException.class,
                () -> repository.updateScreening(
                        createdScreening.screeningId(),
                        new Screening(
                                null,
                                replacementFilm.filmId(),
                                null,
                                "Updated Cinema",
                                "2026-12-05T20:00:00"
                        )
                )
        );

        assertEquals("Failed to update screening", exception.getMessage());
        assertInstanceOf(SQLException.class, exception.getCause());

        Screening persistedScreening =
                screeningRepository.findScreeningById(
                        createdScreening.screeningId()
                ).orElseThrow();

        assertEquals(createdScreening, persistedScreening);
    }

    @Test
    void shouldThrowRepositoryExceptionWhenResultSetCloseFails() {
        Screening createdScreening = createScreening(
                "Test Film",
                "Test Cinema",
                "2026-12-01T18:00:00"
        );

        ConnectionProvider provider = connectionProvider(
                "FROM screenings s",
                false,
                ResourceToFail.RESULT_SET
        );

        ScreeningRepository repository = new SqliteScreeningRepository(
                provider,
                new TransactionManager(provider)
        );

        RepositoryException exception = assertThrows(
                RepositoryException.class,
                () -> repository.findScreeningById(
                        createdScreening.screeningId()
                )
        );

        assertEquals(
                "Failed to retrieve screening",
                exception.getMessage()
        );
    }

    @Test
    void shouldThrowRepositoryExceptionWhenPreparedStatementCloseFails() {
        Screening createdScreening = createScreening(
                "Test Film",
                "Test Cinema",
                "2026-12-01T18:00:00"
        );

        ConnectionProvider provider = connectionProvider(
                "FROM screenings s",
                false,
                ResourceToFail.STATEMENT
        );

        ScreeningRepository repository = new SqliteScreeningRepository(
                provider,
                new TransactionManager(provider)
        );

        RepositoryException exception = assertThrows(
                RepositoryException.class,
                () -> repository.findScreeningById(
                        createdScreening.screeningId()
                )
        );

        assertEquals(
                "Failed to retrieve screening",
                exception.getMessage()
        );
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
    void shouldThrowFilmNotFoundExceptionWhenCreatingScreeningWithNonexistentFilm()
            throws SQLException {

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
    void shouldThrowRepositoryExceptionWhenConnectionProviderIsNull() {
        RepositoryException exception = assertThrows(
                RepositoryException.class,
                () -> new SqliteScreeningRepository(
                        null,
                        new TransactionManager(databaseManager)
                )
        );

        assertEquals(
                "Connection provider must not be null",
                exception.getMessage()
        );
    }

    @Test
    void shouldThrowRepositoryExceptionWhenTransactionManagerIsNull() {
        RepositoryException exception = assertThrows(
                RepositoryException.class,
                () -> new SqliteScreeningRepository(databaseManager, null)
        );

        assertEquals(
                "Transaction manager must not be null",
                exception.getMessage()
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

    private ConnectionProvider connectionProvider(
            String sqlFragment,
            boolean emptyResultSet,
            ResourceToFail resourceToFail) {

        return () -> {
            Connection connection = databaseManager.getConnection();

            return createProxy(
                    Connection.class,
                    (proxy, method, args) -> {
                        Object result = invokeDelegate(
                                connection,
                                method,
                                args
                        );

                        if (method.getName().equals("prepareStatement")
                                && result instanceof PreparedStatement statement
                                && ((String) args[0]).contains(sqlFragment)) {

                            return wrapPreparedStatement(
                                    statement,
                                    emptyResultSet,
                                    resourceToFail
                            );
                        }

                        return result;
                    }
            );
        };
    }

    private PreparedStatement wrapPreparedStatement(
            PreparedStatement statement,
            boolean emptyResultSet,
            ResourceToFail resourceToFail) {

        return createProxy(
                PreparedStatement.class,
                (proxy, method, args) -> {

                    if (method.getName().equals("close")
                            && resourceToFail == ResourceToFail.STATEMENT) {

                        invokeDelegate(statement, method, args);

                        throw new SQLException(
                                "Simulated PreparedStatement close failure"
                        );
                    }

                    Object result = invokeDelegate(
                            statement,
                            method,
                            args
                    );

                    if (method.getName().equals("executeQuery")
                            && result instanceof ResultSet resultSet) {

                        return wrapResultSet(
                                resultSet,
                                emptyResultSet,
                                resourceToFail
                        );
                    }

                    return result;
                }
        );
    }

    private ResultSet wrapResultSet(
            ResultSet resultSet,
            boolean emptyResultSet,
            ResourceToFail resourceToFail) {

        return createProxy(
                ResultSet.class,
                (proxy, method, args) -> {

                    if (method.getName().equals("next") && emptyResultSet) {
                        return false;
                    }

                    if (method.getName().equals("close")
                            && resourceToFail == ResourceToFail.RESULT_SET) {

                        invokeDelegate(resultSet, method, args);

                        throw new SQLException(
                                "Simulated ResultSet close failure"
                        );
                    }

                    return invokeDelegate(resultSet, method, args);
                }
        );
    }

    private <T> T createProxy(
            Class<T> interfaceType,
            InvocationHandler handler) {

        return interfaceType.cast(
                Proxy.newProxyInstance(
                        interfaceType.getClassLoader(),
                        new Class<?>[]{interfaceType},
                        handler
                )
        );
    }

    private Object invokeDelegate(
            Object target,
            Method method,
            Object[] args) throws Throwable {

        try {
            return method.invoke(target, args);
        } catch (InvocationTargetException exception) {
            throw exception.getCause();
        }
    }

    private enum ResourceToFail {
        NONE,
        RESULT_SET,
        STATEMENT
    }

    private ConnectionProvider connectionProviderThrowingSQLException(
            String sqlFragment) {

        return () -> {
            Connection connection = databaseManager.getConnection();

            return createProxy(
                    Connection.class,
                    (proxy, method, args) -> {

                        if (method.getName().equals("prepareStatement")
                                && ((String) args[0]).contains(sqlFragment)) {

                            throw new SQLException(
                                    "Simulated SQL failure"
                            );
                        }

                        return invokeDelegate(
                                connection,
                                method,
                                args
                        );
                    }
            );
        };
    }
}