package com.marquee.db;

import com.marquee.db.transactions.ConnectionProvider;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

public final class DatabaseManager implements ConnectionProvider {

    // Environment variable used to override the default database location.
    private static final String DATABASE_PATH_ENV = "MARQUEE_DB_PATH";

    // Default database file location: project working directory.
    private static final String DEFAULT_DATABASE_PATH = "marquee.db";

    private final String jdbcUrl;

    // Resolves the database path and creates its parent directory if necessary.
    public DatabaseManager() throws IOException {
        this(resolveDatabasePath());
    }

    // Creates a database manager using the specified database file path.
    public DatabaseManager(Path databasePath) throws IOException {
        Path absolutePath = databasePath.toAbsolutePath();

        Path parentDirectory = absolutePath.getParent();

        if (parentDirectory != null) {
            Files.createDirectories(parentDirectory);
        }

        this.jdbcUrl = "jdbc:sqlite:" + absolutePath;
    }

    // Opens a database connection and enables foreign-key enforcement.
    @Override
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

    // Resolves the database path from the environment variable or default location.
    private static Path resolveDatabasePath() {
        String configuredPath = System.getenv(DATABASE_PATH_ENV);

        return Path.of(
                configuredPath == null || configuredPath.isBlank()
                        ? DEFAULT_DATABASE_PATH
                        : configuredPath
        );
    }
}