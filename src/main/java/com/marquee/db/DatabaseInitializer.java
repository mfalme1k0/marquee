package com.marquee.db;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Objects;

public final class DatabaseInitializer {

    private final DatabaseManager databaseManager;

    // Creates an initializer using the application's database manager.
    public DatabaseInitializer(DatabaseManager databaseManager) {
        this.databaseManager = Objects.requireNonNull(
                databaseManager, "databaseManager cannot not be null"
        );
    }

    // Initializes the database schema and prepares it for application use.
    public void initialize() throws SQLException, IOException {
        String schemaScript = loadSqlScript("schema.sql");

        try (Connection connection = databaseManager.getConnection()) {
            connection.setAutoCommit(false);

            try {
                boolean freshDatabase = !hasExistingTables(connection);

                executeSqlScript(connection, schemaScript);

                if (freshDatabase) {
                    String seedScript = loadSqlScript("seed.sql");
                    executeSqlScript(connection, seedScript);
                }

                connection.commit();
            } catch (SQLException | IOException | RuntimeException | Error exception) {
                try {
                    connection.rollback();
                } catch (SQLException rollbackException) {
                    exception.addSuppressed(rollbackException);
                }

                throw exception;
            }
        }

        ensureLogInfrastructure();
    }

    private void ensureLogInfrastructure() throws SQLException {
        String createTable = """
            CREATE TABLE IF NOT EXISTS log_events (
                id            INTEGER PRIMARY KEY AUTOINCREMENT,
                occurred_at   TEXT    NOT NULL,
                level         TEXT    NOT NULL,
                event         TEXT    NOT NULL,
                context_json  TEXT    NOT NULL,
                user_id       INTEGER
            )
            """;

        String createIndex = """
            CREATE INDEX IF NOT EXISTS idx_log_events_occurred_at
                ON log_events (occurred_at)
            """;

        try (Connection connection = databaseManager.getConnection();
             Statement statement = connection.createStatement()) {
            statement.execute(createTable);
            statement.execute(createIndex);
        }
    }

    // Reads the specified SQL resource (e.g., schema.sql or seed.sql) and returns its contents as a string.
    String loadSqlScript(String resourceName) throws IOException {
        try (InputStream inputStream = getClass()
                .getClassLoader()
                .getResourceAsStream(resourceName)) {

            if (inputStream == null) {
                throw new IOException("SQL resource not found: " + resourceName);
            }

            return new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    // Executes each SQL statement in the supplied script (schema.sql or seed.sql)
    private void executeSqlScript(Connection connection, String script) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            for (String sql : script.split(";")) {
                if (!sql.isBlank()) {
                    statement.execute(sql);
                }
            }
        }
    }

    // Checks whether the database already contains application tables.
    private boolean hasExistingTables(Connection connection) throws SQLException {
        String sql = """
            SELECT EXISTS (
                 SELECT 1
                 FROM sqlite_schema
                 WHERE type = 'table'
                   AND name NOT GLOB 'sqlite_*'
            );
            """;

        try (Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery(sql)) {
            return resultSet.getBoolean(1);
        }
    }
}