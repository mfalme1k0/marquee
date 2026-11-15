package com.marquee.db;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

public final class DatabaseManager {

    // Environment variable used to override the default database location which is the root folder.
    private static final String DATABASE_PATH_ENV = "MARQUEE_DB_PATH";

    // Default database file location: project working directory.
    private static final String DEFAULT_DATABASE_PATH = "marquee.db";

    private final String jdbcUrl;

    // Resolves the database path and creates its parent directory if necessary.
    public DatabaseManager() throws IOException {
        String configuredPath = System.getenv(DATABASE_PATH_ENV);

        Path databasePath = Path.of(
                configuredPath == null || configuredPath.isBlank()
                        ? DEFAULT_DATABASE_PATH
                        : configuredPath
        ).toAbsolutePath();

        Path parentDirectory = databasePath.getParent();

        if (parentDirectory != null) {
            Files.createDirectories(parentDirectory);
        }

        this.jdbcUrl = "jdbc:sqlite:" + databasePath;
    }

    // Opens a database connection and enables foreign-key enforcement.
    public Connection getConnection() throws SQLException {
        Connection connection = DriverManager.getConnection(jdbcUrl);

        try (Statement statement = connection.createStatement()) {
            statement.execute("PRAGMA foreign_keys = ON");

            // Waits up to 5 seconds when another connection holds a database lock.
            statement.execute("PRAGMA busy_timeout = 5000");
            return connection;
        } catch (SQLException exception) {
            try {
                connection.close();
            } catch (SQLException closeException) {
                exception.addSuppressed(closeException);
            }

            throw exception;
        }
    }
}
