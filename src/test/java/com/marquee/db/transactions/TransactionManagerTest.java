package com.marquee.db.transactions;

import com.marquee.exceptions.RepositoryException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

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