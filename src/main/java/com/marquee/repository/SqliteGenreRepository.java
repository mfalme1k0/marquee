package com.marquee.repository;

import com.marquee.db.transactions.ConnectionProvider;
import com.marquee.db.transactions.TransactionManager;
import com.marquee.exceptions.DuplicateGenreException;
import com.marquee.exceptions.GenreNotFoundException;
import com.marquee.exceptions.RepositoryException;
import com.marquee.model.Genre;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class SqliteGenreRepository implements GenreRepository {

    private final ConnectionProvider connectionProvider;
    private final TransactionManager transactionManager;

    // Creates a genre repository using the connection provider and transaction manager.
    public SqliteGenreRepository(
            ConnectionProvider connectionProvider,
            TransactionManager transactionManager) {
        if (connectionProvider == null) {
            throw new RepositoryException("Connection provider must not be null");
        }

        if (transactionManager == null) {
            throw new RepositoryException("Transaction manager must not be null");
        }

        this.connectionProvider = connectionProvider;
        this.transactionManager = transactionManager;
    }

    // Creates a genre and returns it with its generated database ID.
    @Override
    public Genre createGenre(Genre genre) {
        if (genre == null) {
            throw new RepositoryException("Genre must not be null");
        }

        return transactionManager.executeInTransaction(connection -> {
            String sql = """
                    INSERT INTO genres (name)
                    VALUES (?)
                    """;

            try {
                try (PreparedStatement statement = connection.prepareStatement(sql)) {
                    statement.setString(1, genre.name());
                    statement.executeUpdate();
                }

                try (PreparedStatement idStatement = connection.prepareStatement(
                        "SELECT last_insert_rowid()");
                     ResultSet resultSet = idStatement.executeQuery()) {

                    resultSet.next();
                    return new Genre(resultSet.getLong(1), genre.name());
                }

            } catch (SQLException exception) {
                if (isUniqueConstraintViolation(exception)) {
                    throw new DuplicateGenreException(genre.name());
                }

                throw new RepositoryException("Failed to create genre", exception);
            }
        });
    }

    // Retrieves a genre by its ID.
    // Returns an empty Optional when no genre matches the supplied ID.
    @Override
    public Optional<Genre> findGenreById(long genreId) {
        String sql = """
                SELECT genre_id, name
                FROM genres
                WHERE genre_id = ?
                """;

        return withConnection(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setLong(1, genreId);

                try (ResultSet resultSet = statement.executeQuery()) {
                    if (!resultSet.next()) {
                        return Optional.empty();
                    }

                    return Optional.of(mapGenre(resultSet));
                }
            }
        });
    }

    // Retrieves a genre by its unique name.
    // Returns an empty Optional when no genre matches the supplied name.
    @Override
    public Optional<Genre> findGenreByName(String name) {
        String sql = """
                SELECT genre_id, name
                FROM genres
                WHERE name = ?
                """;

        return withConnection(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setString(1, name);

                try (ResultSet resultSet = statement.executeQuery()) {
                    if (!resultSet.next()) {
                        return Optional.empty();
                    }

                    return Optional.of(mapGenre(resultSet));
                }
            }
        });
    }

    // Retrieves all genres ordered alphabetically by name.
    @Override
    public List<Genre> findAllGenres() {
        String sql = """
                SELECT genre_id, name
                FROM genres
                ORDER BY name
                """;

        return withConnection(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(sql);
                 ResultSet resultSet = statement.executeQuery()) {

                List<Genre> genres = new ArrayList<>();

                while (resultSet.next()) {
                    genres.add(mapGenre(resultSet));
                }

                return List.copyOf(genres);
            }
        });
    }

    // Updates an existing genre's name.
    // Throws GenreNotFoundException when the specified genre does not exist.
    @Override
    public Genre updateGenre(long genreId, Genre genre) {
        if (genre == null) {
            throw new RepositoryException("Genre must not be null");
        }

        String sql = """
                UPDATE genres
                SET name = ?
                WHERE genre_id = ?
                """;

        try (Connection connection = connectionProvider.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {

            statement.setString(1, genre.name());
            statement.setLong(2, genreId);

            if (statement.executeUpdate() == 0) {
                throw new GenreNotFoundException(genreId);
            }

            return new Genre(genreId, genre.name());

        } catch (SQLException exception) {
            if (isUniqueConstraintViolation(exception)) {
                throw new DuplicateGenreException(genre.name());
            }

            throw new RepositoryException("Failed to update genre", exception);
        }
    }

    // Deletes a genre by its ID.
    // Foreign-key cascades remove its film associations without deleting films.
    @Override
    public boolean deleteGenreById(long genreId) {
        String sql = "DELETE FROM genres WHERE genre_id = ?";

        return withConnection(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setLong(1, genreId);
                return statement.executeUpdate() > 0;
            }
        });
    }

    // Checks whether the SQL exception represents a unique-constraint violation.
    private boolean isUniqueConstraintViolation(SQLException exception) {
        return exception.getErrorCode() == 2067;
    }

    // Maps the current result-set row to a Genre record.
    private Genre mapGenre(ResultSet resultSet) throws SQLException {
        return new Genre(
                resultSet.getLong("genre_id"),
                resultSet.getString("name")
        );
    }

    // Executes a standalone database operation and closes its connection afterward.
    // Converts JDBC failures into the repository's unchecked exception hierarchy.
    private <T> T withConnection(SqlFunction<T> operation) {
        try (Connection connection = connectionProvider.getConnection()) {
            return operation.apply(connection);
        } catch (SQLException exception) {
            throw new RepositoryException("Database operation failed", exception);
        }
    }

    @FunctionalInterface
    private interface SqlFunction<T> {
        T apply(Connection connection) throws SQLException;
    }
}