package com.marquee.repository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.marquee.db.DatabaseInitializer;
import com.marquee.db.DatabaseManager;
import com.marquee.db.transactions.TransactionManager;
import com.marquee.model.LogEntry;
import com.marquee.repository.SqliteLogRepository.LogPersistenceException;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SqliteLogRepositoryTest {

    private static final LocalDateTime WHEN = LocalDateTime.of(2026, 11, 26, 10, 15, 30);

    @TempDir
    Path tempDir;

    private DatabaseManager databaseManager;
    private SqliteLogRepository repository;

    @BeforeEach
    void setUp() throws Exception {
        databaseManager = new DatabaseManager(tempDir.resolve("logs.db"));
        new DatabaseInitializer(databaseManager).initialize();
        repository = new SqliteLogRepository(databaseManager, new TransactionManager(databaseManager));
    }

    @Test
    void appendStoresEveryField() throws Exception {
        repository.append(LogEntry.newEvent(WHEN, "INFO", "film.created", "{\"film_id\":1}", 7L));

        List<Row> rows = rows();
        assertEquals(1, rows.size());
        Row row = rows.get(0);
        assertNotNull(row.id());
        assertEquals(WHEN, LocalDateTime.parse(row.occurredAt()));
        assertEquals("INFO", row.level());
        assertEquals("film.created", row.event());
        assertEquals("{\"film_id\":1}", row.contextJson());
        assertEquals(7L, row.userId());
    }

    @Test
    void appendStoresNullUserIdAsSqlNull() throws Exception {
        repository.append(LogEntry.newEvent(WHEN, "INFO", "app.starting", "{}", null));

        assertNull(rows().get(0).userId());
    }

    @Test
    void appendAssignsIncreasingIdsAndKeepsInsertionOrder() throws Exception {
        repository.append(LogEntry.newEvent(WHEN, "INFO", "first", "{}", null));
        repository.append(LogEntry.newEvent(WHEN, "WARN", "second", "{}", null));
        repository.append(LogEntry.newEvent(WHEN, "ERROR", "third", "{}", null));

        List<Row> rows = rows();
        assertEquals(List.of("first", "second", "third"), rows.stream().map(Row::event).toList());
        assertTrue(rows.get(0).id() < rows.get(1).id());
        assertTrue(rows.get(1).id() < rows.get(2).id());
    }

    @Test
    void appendStoresContextJsonVerbatimIncludingQuotesAndUnicode() throws Exception {
        String json = "{\"title\":\"Amélie \\\"Poulain\\\"\",\"note\":\"'; DROP TABLE log_events; --\"}";

        repository.append(LogEntry.newEvent(WHEN, "INFO", "film.created", json, null));

        assertEquals(json, rows().get(0).contextJson());
        assertEquals(1, rows().size());
    }

    @Test
    void appendWrapsDatabaseFailureInLogPersistenceException() throws Exception {
        try (Connection connection = databaseManager.getConnection();
             Statement statement = connection.createStatement()) {
            statement.execute("DROP TABLE log_events");
        }

        LogPersistenceException thrown = assertThrows(
                LogPersistenceException.class,
                () -> repository.append(LogEntry.newEvent(WHEN, "INFO", "film.created", "{}", null))
        );

        assertInstanceOf(SQLException.class, thrown.getCause());
    }

    @Test
    void appendFailureIsUncheckedSoCallersCanCatchRuntimeException() {
        assertTrue(RuntimeException.class.isAssignableFrom(LogPersistenceException.class));
    }

    @Test
    void appendRejectsEntryViolatingNotNullConstraint() {
        assertThrows(
                LogPersistenceException.class,
                () -> repository.append(LogEntry.newEvent(WHEN, "INFO", null, "{}", null))
        );
        assertEquals(0, countRows());
    }

    @Test
    void logsSurviveRollbackOfSurroundingBusinessTransaction() {
        TransactionManager transactionManager = new TransactionManager(databaseManager);

        assertThrows(
                IllegalStateException.class,
                () -> transactionManager.executeInTransaction(connection -> {
                    repository.append(LogEntry.newEvent(WHEN, "WARN", "film.create.rejected", "{}", null));
                    throw new IllegalStateException("business failure");
                })
        );

        assertEquals(1, countRows());
    }

    private int countRows() {
        try {
            return rows().size();
        } catch (SQLException e) {
            throw new AssertionError(e);
        }
    }

    private List<Row> rows() throws SQLException {
        List<Row> rows = new ArrayList<>();
        try (Connection connection = databaseManager.getConnection();
             Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery(
                     "SELECT id, occurred_at, level, event, context_json, user_id FROM log_events ORDER BY id")) {
            while (rs.next()) {
                long userIdValue = rs.getLong("user_id");
                Long userId = rs.wasNull() ? null : userIdValue; // must be read right after getLong
                rows.add(new Row(
                        rs.getLong("id"),
                        rs.getString("occurred_at"),
                        rs.getString("level"),
                        rs.getString("event"),
                        rs.getString("context_json"),
                        userId));
            }
        }
        return rows;
    }

    private record Row(Long id, String occurredAt, String level, String event, String contextJson, Long userId) {}
}
