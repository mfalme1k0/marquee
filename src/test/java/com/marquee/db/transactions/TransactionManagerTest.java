package com.marquee.db.transactions;

import com.marquee.exceptions.RepositoryException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.lang.reflect.Proxy;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class TransactionManagerTest {

    @TempDir
    Path tempDir;

    @Test
    void shouldCommitSuccessfulTransaction() throws SQLException {
        Path databasePath = tempDir.resolve("transactions.db");

        createDatabaseTable(databasePath);

        TransactionManager transactionManager = createTransactionManager(databasePath);

        transactionManager.executeInTransaction(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(
                    "INSERT INTO test_records (name) VALUES (?)")) {

                statement.setString(1, "Committed record");
                statement.executeUpdate();
            }

            return null;
        });

        assertEquals(1, countRecords(databasePath));
    }

    @Test
    void shouldRollbackTransactionWhenSqlExceptionOccurs() throws SQLException {
        Path databasePath = tempDir.resolve("transactions.db");

        createDatabaseTable(databasePath);

        TransactionManager transactionManager = createTransactionManager(databasePath);

        assertThrows(RepositoryException.class, () ->
                transactionManager.executeInTransaction(connection -> {
                    try (PreparedStatement statement = connection.prepareStatement(
                            "INSERT INTO test_records (name) VALUES (?)")) {

                        statement.setString(1, "Uncommitted record");
                        statement.executeUpdate();
                    }

                    throw new SQLException("Simulated database failure");
                })
        );

        assertEquals(0, countRecords(databasePath));
    }

    @Test
    void shouldAttachRollbackExceptionAsSuppressedWhenRollbackFails() {
        SQLException originalException =
                new SQLException("Simulated transaction failure");

        SQLException rollbackException =
                new SQLException("Simulated rollback failure");

        Connection connection = (Connection) Proxy.newProxyInstance(
                Connection.class.getClassLoader(),
                new Class<?>[]{Connection.class},
                (proxy, method, args) -> {
                    if (method.getName().equals("rollback")) {
                        throw rollbackException;
                    }

                    return null;
                }
        );

        ConnectionProvider connectionProvider = () -> connection;

        TransactionManager transactionManager =
                new TransactionManager(connectionProvider);

        RepositoryException exception = assertThrows(
                RepositoryException.class,
                () -> transactionManager.executeInTransaction(ignored -> {
                    throw originalException;
                })
        );

        assertSame(originalException, exception.getCause());

        assertEquals(1, originalException.getSuppressed().length);

        assertSame(
                rollbackException,
                originalException.getSuppressed()[0]
        );
    }

    @Test
    void shouldThrowRepositoryExceptionWhenConnectionProviderIsNull() {
        RepositoryException exception = assertThrows(
                RepositoryException.class,
                () -> new TransactionManager(null)
        );

        assertEquals(
                "Connection provider must not be null",
                exception.getMessage()
        );
    }

    @Test
    void shouldThrowRepositoryExceptionWhenTransactionCallbackIsNull() {
        TransactionManager transactionManager =
                createTransactionManager(tempDir.resolve("transactions.db"));

        RepositoryException exception = assertThrows(
                RepositoryException.class,
                () -> transactionManager.executeInTransaction(null)
        );

        assertEquals(
                "Transaction callback must not be null",
                exception.getMessage()
        );
    }

    @Test
    void shouldCloseConnectionAfterSuccessfulTransaction() throws SQLException {
        Path databasePath = tempDir.resolve("transactions.db");

        AtomicReference<Connection> connectionReference = new AtomicReference<>();

        ConnectionProvider connectionProvider = () -> {
            Connection connection = DriverManager.getConnection(
                    "jdbc:sqlite:" + databasePath.toAbsolutePath()
            );

            connectionReference.set(connection);

            return connection;
        };

        TransactionManager transactionManager = new TransactionManager(connectionProvider);

        transactionManager.executeInTransaction(connection -> null);

        assertNotNull(connectionReference.get());
        assertTrue(connectionReference.get().isClosed());
    }

    private TransactionManager createTransactionManager(Path databasePath) {
        ConnectionProvider connectionProvider = () ->
                DriverManager.getConnection(
                        "jdbc:sqlite:" + databasePath.toAbsolutePath()
                );

        return new TransactionManager(connectionProvider);
    }

    private void createDatabaseTable(Path databasePath) throws SQLException {
        try (Connection connection = DriverManager.getConnection(
                "jdbc:sqlite:" + databasePath.toAbsolutePath());
             Statement statement = connection.createStatement()) {

            statement.execute("""
                    CREATE TABLE test_records (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        name TEXT NOT NULL
                    )
                    """);
        }
    }

    private int countRecords(Path databasePath) throws SQLException {
        try (Connection connection = DriverManager.getConnection(
                "jdbc:sqlite:" + databasePath.toAbsolutePath());
             Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery(
                     "SELECT COUNT(*) FROM test_records")) {

            resultSet.next();

            return resultSet.getInt(1);
        }
    }
}