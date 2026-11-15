package com.marquee.db.transactions;

import com.marquee.exceptions.RepositoryException;

import java.sql.Connection;
import java.sql.SQLException;

// Manages database transactions and their connection lifecycle.
public final class TransactionManager {

    private final ConnectionProvider connectionProvider;

    // Creates a transaction manager using the specified connection provider.
    // @param connectionProvider the provider used to obtain database connections
    public TransactionManager(ConnectionProvider connectionProvider) {
        if (connectionProvider == null) {
            throw new RepositoryException("Connection provider must not be null");
        }

        this.connectionProvider = connectionProvider;
    }

    // Executes an operation within a database transaction.
    // If the operation fails, the transaction is rolled back before the original exception or error is propagated.
    // @param callback the operation to execute within the transaction
    // throws RepositoryException if a database transaction fails
    public <T> T executeInTransaction(TransactionCallback<T> callback) {
        if (callback == null) {
            throw new RepositoryException("Transaction callback must not be null");
        }

        try (Connection connection = connectionProvider.getConnection()) {
            connection.setAutoCommit(false);

            try {
                T result = callback.execute(connection);

                connection.commit();

                return result;
            } catch (SQLException | RuntimeException | Error exception) {
                rollback(connection, exception);

                throw exception;
            }
        } catch (SQLException exception) {
            throw new RepositoryException("Database transaction failed", exception);
        }
    }

    // Rolls back the current transaction.
    // If rollback itself fails, the rollback exception is attached to the original exception rather than replacing it.
    // @param connection the connection whose transaction should be rolled back
    // @param originalException the exception that caused the rollback
    private void rollback(Connection connection, Throwable originalException) {
        try {
            connection.rollback();
        } catch (SQLException rollbackException) {
            originalException.addSuppressed(rollbackException);
        }
    }

    // Represents an operation that executes using the transaction's connection.
    @FunctionalInterface
    public interface TransactionCallback<T> {

        // Executes the operation using the supplied database connection.
        // @param connection the connection belonging to the current transaction.
        // Throws SQLException if a database operation fails.
        T execute(Connection connection) throws SQLException;
    }
}