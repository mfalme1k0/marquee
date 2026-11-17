package com.marquee.db;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.lang.reflect.Proxy;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.*;

class DatabaseInitializerTest {

    @TempDir
    Path tempDir;

    private DatabaseManager databaseManager;
    private DatabaseInitializer databaseInitializer;

    @BeforeEach
    void setUp() throws Exception {
        databaseManager = new DatabaseManager(
                tempDir.resolve("marquee.db")
        );

        databaseInitializer = new DatabaseInitializer(databaseManager);
    }

    @Test
    void shouldCreateAllDatabaseTables() throws Exception {
        databaseInitializer.initialize();

        assertTrue(tableExists("films"));
        assertTrue(tableExists("genres"));
        assertTrue(tableExists("film_genres"));
        assertTrue(tableExists("screenings"));
    }

    @Test
    void shouldCreateAllDatabaseIndexes() throws Exception {
        databaseInitializer.initialize();

        assertTrue(indexExists("idx_film_genres_genre"));
        assertTrue(indexExists("idx_screenings_film"));
        assertTrue(indexExists("idx_screenings_venue"));
        assertTrue(indexExists("idx_screenings_time"));
    }

    @Test
    void shouldInsertSeedDataWhenInitializingFreshDatabase()
            throws Exception {

        databaseInitializer.initialize();

        assertEquals(15, countRows("films"));
        assertEquals(10, countRows("genres"));
        assertEquals(38, countRows("film_genres"));
        assertEquals(15, countRows("screenings"));
    }

    @Test
    void shouldNotInsertDuplicateSeedDataWhenInitializingExistingDatabase()
            throws Exception {

        databaseInitializer.initialize();

        databaseInitializer.initialize();

        assertEquals(15, countRows("films"));
        assertEquals(10, countRows("genres"));
        assertEquals(38, countRows("film_genres"));
        assertEquals(15, countRows("screenings"));
    }

    @Test
    void shouldPreserveExistingRecordsWhenInitializingExistingDatabase()
            throws Exception {

        databaseInitializer.initialize();

        try (Connection connection = databaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement("""
                     INSERT INTO films (title, year, description)
                     VALUES (?, ?, ?)
                     """)) {

            statement.setString(1, "Custom Test Film");
            statement.setInt(2, 2026);
            statement.setString(3, "A film created during testing.");

            statement.executeUpdate();
        }

        databaseInitializer.initialize();

        assertEquals(16, countRows("films"));
        assertEquals(1, countRows(
                "films",
                "title = 'Custom Test Film'"
        ));
    }

    @Test
    void shouldThrowIOExceptionWhenSqlResourceDoesNotExist() {
        IOException exception = assertThrows(
                IOException.class,
                () -> databaseInitializer.loadSqlScript(
                        "missing-test-resource.sql"
                )
        );

        assertEquals(
                "SQL resource not found: missing-test-resource.sql",
                exception.getMessage()
        );
    }

    @Test
    void shouldRollbackTransactionWhenSchemaExecutionFails()
            throws Exception {

        SQLException executionException =
                new SQLException("Schema execution failed");

        boolean[] rollbackCalled = {false};

        DatabaseManager failingDatabaseManager =
                createFailingDatabaseManager(
                        executionException,
                        null,
                        rollbackCalled
                );

        DatabaseInitializer initializer =
                new DatabaseInitializer(failingDatabaseManager);

        SQLException thrownException = assertThrows(
                SQLException.class,
                initializer::initialize
        );

        assertSame(executionException, thrownException);
        assertTrue(rollbackCalled[0]);
        assertEquals(0, thrownException.getSuppressed().length);
    }

    @Test
    void shouldSuppressRollbackFailureWhenInitializationFails()
            throws Exception {

        SQLException executionException =
                new SQLException("Schema execution failed");

        SQLException rollbackException =
                new SQLException("Rollback failed");

        boolean[] rollbackCalled = {false};

        DatabaseManager failingDatabaseManager =
                createFailingDatabaseManager(
                        executionException,
                        rollbackException,
                        rollbackCalled
                );

        DatabaseInitializer initializer =
                new DatabaseInitializer(failingDatabaseManager);

        SQLException thrownException = assertThrows(
                SQLException.class,
                initializer::initialize
        );

        assertSame(executionException, thrownException);
        assertTrue(rollbackCalled[0]);

        assertEquals(1, thrownException.getSuppressed().length);
        assertSame(rollbackException, thrownException.getSuppressed()[0]);
    }

    private boolean tableExists(String tableName) throws SQLException {
        String sql = """
                SELECT EXISTS (
                    SELECT 1
                    FROM sqlite_schema
                    WHERE type = 'table'
                      AND name = ?
                )
                """;

        try (Connection connection = databaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {

            statement.setString(1, tableName);

            try (ResultSet resultSet = statement.executeQuery()) {
                assertTrue(resultSet.next());

                return resultSet.getBoolean(1);
            }
        }
    }

    private boolean indexExists(String indexName) throws SQLException {
        String sql = """
                SELECT EXISTS (
                    SELECT 1
                    FROM sqlite_schema
                    WHERE type = 'index'
                      AND name = ?
                )
                """;

        try (Connection connection = databaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {

            statement.setString(1, indexName);

            try (ResultSet resultSet = statement.executeQuery()) {
                assertTrue(resultSet.next());

                return resultSet.getBoolean(1);
            }
        }
    }

    private int countRows(String tableName) throws SQLException {
        return countRows(tableName, null);
    }

    private int countRows(String tableName, String condition)
            throws SQLException {

        String sql = "SELECT COUNT(*) FROM " + tableName;

        if (condition != null) {
            sql += " WHERE " + condition;
        }

        try (Connection connection = databaseManager.getConnection();
             Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery(sql)) {

            assertTrue(resultSet.next());

            return resultSet.getInt(1);
        }
    }

    private DatabaseManager createFailingDatabaseManager(
            SQLException executionException,
            SQLException rollbackException,
            boolean[] rollbackCalled
    ) throws Exception {

        ResultSet resultSet = (ResultSet) Proxy.newProxyInstance(
                ResultSet.class.getClassLoader(),
                new Class<?>[]{ResultSet.class},
                (proxy, method, args) -> {

                    if (method.getName().equals("getBoolean")) {
                        return false;
                    }

                    return null;
                }
        );

        Statement statement = (Statement) Proxy.newProxyInstance(
                Statement.class.getClassLoader(),
                new Class<?>[]{Statement.class},
                (proxy, method, args) -> {

                    if (method.getName().equals("executeQuery")) {
                        return resultSet;
                    }

                    if (method.getName().equals("execute")) {
                        String sql = (String) args[0];

                        if (!sql.startsWith("PRAGMA")) {
                            throw executionException;
                        }

                        return false;
                    }

                    return null;
                }
        );

        Connection connection = (Connection) Proxy.newProxyInstance(
                Connection.class.getClassLoader(),
                new Class<?>[]{Connection.class},
                (proxy, method, args) -> {

                    if (method.getName().equals("createStatement")) {
                        return statement;
                    }

                    if (method.getName().equals("rollback")) {
                        rollbackCalled[0] = true;

                        if (rollbackException != null) {
                            throw rollbackException;
                        }

                        return null;
                    }

                    return null;
                }
        );

        return new DatabaseManager(
                tempDir.resolve("failing.db"),
                jdbcUrl -> connection
        );
    }
}