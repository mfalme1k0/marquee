// src/main/java/com/marquee/repository/SqliteLogRepository.java
package com.marquee.repository;

import com.marquee.db.DatabaseManager;
import com.marquee.db.transactions.TransactionManager;
import com.marquee.model.LogEntry;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Types;

public class SqliteLogRepository implements LogRepository {

    private static final String INSERT = """
            INSERT INTO log_events (occurred_at, level, event, context_json, user_id)
            VALUES (?, ?, ?, ?, ?)
            """;

    private final DatabaseManager databaseManager;

    public SqliteLogRepository(DatabaseManager databaseManager, TransactionManager tx) {
        this.databaseManager = databaseManager;
        // tx reserved for symmetry with the other repos; the logger
        // intentionally does not join caller transactions — logs should
        // survive a rolled-back business transaction.
    }

    @Override
    public void append(LogEntry entry) {
        try (Connection c = databaseManager.getConnection();
             PreparedStatement ps = c.prepareStatement(INSERT)) {
            ps.setString(1, entry.occurredAt().toString());
            ps.setString(2, entry.level());
            ps.setString(3, entry.event());
            ps.setString(4, entry.contextJson());
            if (entry.userId() == null) {
                ps.setNull(5, Types.INTEGER);
            } else {
                ps.setLong(5, entry.userId());
            }
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new LogPersistenceException(e);
        }
    }

    public static final class LogPersistenceException extends RuntimeException {
        public LogPersistenceException(Throwable cause) { super(cause); }
    }
}