package com.marquee.db;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.*;

class DatabaseManagerTest {

    @TempDir
    Path tempDir;

    @Test
    void shouldCreateDatabaseFileWhenConnectionIsOpened() throws Exception {
        Path databasePath = tempDir.resolve("nested").resolve("marquee.db");

        DatabaseManager databaseManager = new DatabaseManager(databasePath);

        assertFalse(Files.exists(databasePath));

        try (Connection connection = databaseManager.getConnection()) {
            assertNotNull(connection);
            assertTrue(Files.exists(databasePath));
        }
    }

    @Test
    void shouldEnableForeignKeyEnforcementOnEveryConnection() throws Exception {
        DatabaseManager databaseManager =
                new DatabaseManager(tempDir.resolve("marquee.db"));

        try (Connection firstConnection = databaseManager.getConnection();
             Connection secondConnection = databaseManager.getConnection()) {

            assertTrue(isForeignKeyEnforcementEnabled(firstConnection));
            assertTrue(isForeignKeyEnforcementEnabled(secondConnection));
        }
    }

    @Test
    void shouldConfigureBusyTimeoutOnEveryConnection() throws Exception {
        DatabaseManager databaseManager =
                new DatabaseManager(tempDir.resolve("marquee.db"));

        try (Connection firstConnection = databaseManager.getConnection();
             Connection secondConnection = databaseManager.getConnection()) {

            assertEquals(5000, getBusyTimeout(firstConnection));
            assertEquals(5000, getBusyTimeout(secondConnection));
        }
    }

    @Test
    void shouldRejectInsertWhenForeignKeyDoesNotExist() throws Exception {
        DatabaseManager databaseManager =
                new DatabaseManager(tempDir.resolve("marquee.db"));

        try (Connection connection = databaseManager.getConnection();
             Statement statement = connection.createStatement()) {

            statement.execute("""
                    CREATE TABLE parent (
                        id INTEGER PRIMARY KEY
                    )
                    """);

            statement.execute("""
                    CREATE TABLE child (
                        id INTEGER PRIMARY KEY,
                        parent_id INTEGER NOT NULL,
                        FOREIGN KEY (parent_id) REFERENCES parent(id)
                    )
                    """);

            assertThrows(SQLException.class, () ->
                    statement.executeUpdate(
                            "INSERT INTO child (id, parent_id) VALUES (1, 999)"
                    )
            );
        }
    }

    @Test
    void shouldUseDefaultPathWhenConfiguredPathIsNull() {
        assertEquals(
                Path.of("marquee.db"),
                DatabaseManager.resolveDatabasePath(null)
        );
    }

    @Test
    void shouldUseDefaultPathWhenConfiguredPathIsBlank() {
        assertEquals(
                Path.of("marquee.db"),
                DatabaseManager.resolveDatabasePath("   ")
        );
    }

    @Test
    void shouldUseConfiguredPathWhenItIsNotBlank() {
        assertEquals(
                Path.of("custom/marquee.db"),
                DatabaseManager.resolveDatabasePath("custom/marquee.db")
        );
    }

    @Test
    void shouldCreateDatabaseManagerWhenDatabasePathHasNoParentDirectory()
            throws Exception {

        assertDoesNotThrow(() -> new DatabaseManager(Path.of("/")));
    }

    @Test
    void shouldCreateDatabaseManagerUsingDefaultConstructor()
            throws Exception {

        assertDoesNotThrow(() -> new DatabaseManager());
    }

    @Test
    void shouldCloseConnectionWhenPragmaExecutionFails() throws Exception {
        SQLException executionException =
                new SQLException("PRAGMA execution failed");

        boolean[] connectionClosed = {false};

        Connection connection = createFailingConnection(
                executionException,
                null,
                connectionClosed
        );

        DatabaseManager databaseManager = new DatabaseManager(
                tempDir.resolve("marquee.db"),
                jdbcUrl -> connection
        );

        SQLException thrownException = assertThrows(
                SQLException.class,
                databaseManager::getConnection
        );

        assertSame(executionException, thrownException);
        assertTrue(connectionClosed[0]);
        assertEquals(0, thrownException.getSuppressed().length);
    }

    @Test
    void shouldSuppressConnectionCloseFailureWhenPragmaExecutionFails()
            throws Exception {

        SQLException executionException =
                new SQLException("PRAGMA execution failed");

        SQLException closeException =
                new SQLException("Connection close failed");

        boolean[] connectionClosed = {false};

        Connection connection = createFailingConnection(
                executionException,
                closeException,
                connectionClosed
        );

        DatabaseManager databaseManager = new DatabaseManager(
                tempDir.resolve("marquee.db"),
                jdbcUrl -> connection
        );

        SQLException thrownException = assertThrows(
                SQLException.class,
                databaseManager::getConnection
        );

        assertSame(executionException, thrownException);
        assertTrue(connectionClosed[0]);

        assertEquals(1, thrownException.getSuppressed().length);
        assertSame(closeException, thrownException.getSuppressed()[0]);
    }

    private boolean isForeignKeyEnforcementEnabled(Connection connection)
            throws SQLException {

        try (Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery("PRAGMA foreign_keys")) {

            assertTrue(resultSet.next());

            return resultSet.getInt(1) == 1;
        }
    }

    private int getBusyTimeout(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery("PRAGMA busy_timeout")) {

            assertTrue(resultSet.next());

            return resultSet.getInt(1);
        }
    }

    private Connection createFailingConnection(
            SQLException executionException,
            SQLException closeException,
            boolean[] connectionClosed
    ) {

        Statement statement = (Statement) Proxy.newProxyInstance(
                Statement.class.getClassLoader(),
                new Class<?>[]{Statement.class},
                (proxy, method, args) -> {

                    if (method.getName().equals("execute")) {
                        throw executionException;
                    }

                    if (method.getName().equals("close")) {
                        return null;
                    }

                    return null;
                }
        );

        return (Connection) Proxy.newProxyInstance(
                Connection.class.getClassLoader(),
                new Class<?>[]{Connection.class},
                (proxy, method, args) -> {

                    if (method.getName().equals("createStatement")) {
                        return statement;
                    }

                    if (method.getName().equals("close")) {
                        connectionClosed[0] = true;

                        if (closeException != null) {
                            throw closeException;
                        }

                        return null;
                    }

                    return null;
                }
        );
    }
}