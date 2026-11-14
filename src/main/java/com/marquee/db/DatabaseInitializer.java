package com.marquee.db;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Objects;

public class DatabaseInitializer {

    private final DatabaseManager databaseManager;

    // Creates an initializer using the application's database manager.
    public DatabaseInitializer(DatabaseManager databaseManager) {
        this.databaseManager = Objects.requireNonNull(
                databaseManager,
                "databaseManager must not be null"
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
            } catch (SQLException | IOException exception) {
                try {
                    connection.rollback();
                } catch (SQLException rollbackException) {
                    exception.addSuppressed(rollbackException);
                }

                throw exception;
            }
        }
    }


    // Reads the specified SQL resource (e.g., schema.sql or seed.sql) and returns its contents as a string.
    private String loadSqlScript(String resourceName) throws IOException {
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
                   AND name NOT LIKE 'sqlite_%'
            );
            """;

        try (Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery(sql)) {
            return resultSet.getBoolean(1);
        }
    }
}
