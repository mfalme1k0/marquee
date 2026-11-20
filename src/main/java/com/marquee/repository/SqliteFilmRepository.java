package com.marquee.repository;

import com.marquee.db.transactions.ConnectionProvider;
import com.marquee.db.transactions.TransactionManager;
import com.marquee.exceptions.FilmNotFoundException;
import com.marquee.exceptions.GenreNotFoundException;
import com.marquee.exceptions.RepositoryException;
import com.marquee.model.Film;
import com.marquee.model.Genre;
import com.marquee.model.Page;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class SqliteFilmRepository implements FilmRepository {

    private final ConnectionProvider connectionProvider;
    private final TransactionManager transactionManager;

    // Creates a film repository using the connection provider and transaction manager.
    public SqliteFilmRepository(
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

    // Creates a film and associates its existing genres within one transaction.
    // Returns the persisted film with its generated ID, timestamp, and genres.
    @Override
    public Film createFilm(Film film) {
        if (film == null) {
            throw new RepositoryException("Film must not be null");
        }

        return transactionManager.executeInTransaction(connection -> {
            String sql = """
                    INSERT INTO films (title, year, description)
                    VALUES (?, ?, ?)
                    """;

            long filmId;

            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setString(1, film.title());
                statement.setInt(2, film.year());

                if (film.description() == null) {
                    statement.setNull(3, Types.VARCHAR);
                } else {
                    statement.setString(3, film.description());
                }

                statement.executeUpdate();
            }

            try (Statement statement = connection.createStatement();
                 ResultSet resultSet = statement.executeQuery(
                         "SELECT last_insert_rowid()")) {
                resultSet.next();
                filmId = resultSet.getLong(1);
            }

            insertGenres(connection, filmId, film.genres());

            return findById(connection, filmId)
                    .orElseThrow(() ->
                            new RepositoryException(
                                    "Created film could not be retrieved: " + filmId
                            ));
        });
    }

    // Retrieves a film by its ID, including all its associated genres.
    // Returns an empty Optional when no film matches the supplied ID.
    @Override
    public Optional<Film> findFilmById(long filmId) {
        return withConnection(connection -> findById(connection, filmId));
    }

    // Retrieves a page of films and the total number of available films.
    // Both queries use the same transaction to maintain a consistent snapshot.
    @Override
    public Page<Film> findAllFilms(int page, int perPage) {
        validatePagination(page, perPage);

        return transactionManager.executeInTransaction(connection -> {
            long total = countFilms(connection);
            List<Film> films = findPage(connection, page, perPage);

            return new Page<>(page, perPage, total, films);
        });
    }

    // Retrieves a page of films whose titles contain the supplied search text.
    // Matching is partial, and the total count only includes matching films.
    @Override
    public Page<Film> findFilmsByTitle(String title, int page, int perPage) {
        if (title == null || title.isBlank()) {
            throw new RepositoryException("Title search text must not be blank");
        }

        validatePagination(page, perPage);

        return transactionManager.executeInTransaction(connection -> {
            String whereClause = "f.title LIKE ?";

            String searchPattern = "%" + title.trim() + "%";

            long total = countFilteredFilms(
                    connection,
                    whereClause,
                    statement -> statement.setString(1, searchPattern)
            );

            List<Film> films = findFilteredPage(
                    connection,
                    whereClause,
                    statement -> statement.setString(1, searchPattern),
                    page,
                    perPage
            );

            return new Page<>(page, perPage, total, films);
        });
    }

    // Retrieves a page of films released in the specified year.
    // The total count only includes films matching that year.
    @Override
    public Page<Film> findFilmsByYear(int year, int page, int perPage) {
        validatePagination(page, perPage);

        return transactionManager.executeInTransaction(connection -> {
            String whereClause = "f.year = ?";

            long total = countFilteredFilms(
                    connection,
                    whereClause,
                    statement -> statement.setInt(1, year)
            );

            List<Film> films = findFilteredPage(
                    connection,
                    whereClause,
                    statement -> statement.setInt(1, year),
                    page,
                    perPage
            );

            return new Page<>(page, perPage, total, films);
        });
    }

    // Retrieves a page of films associated with the specified genre.
    // EXISTS avoids duplicate films if the underlying query is extended later.
    @Override
    public Page<Film> findFilmsByGenreId(
            long genreId,
            int page,
            int perPage) {

        validatePagination(page, perPage);

        return transactionManager.executeInTransaction(connection -> {
            String whereClause = """
                EXISTS (
                    SELECT 1
                    FROM film_genres filtered_fg
                    WHERE filtered_fg.film_id = f.film_id
                      AND filtered_fg.genre_id = ?
                )
                """;

            long total = countFilteredFilms(
                    connection,
                    whereClause,
                    statement -> statement.setLong(1, genreId)
            );

            List<Film> films = findFilteredPage(
                    connection,
                    whereClause,
                    statement -> statement.setLong(1, genreId),
                    page,
                    perPage
            );

            return new Page<>(page, perPage, total, films);
        });
    }

    // Updates a film's details and replaces its genre associations atomically.
    // Throws FilmNotFoundException when the specified film does not exist.
    @Override
    public Film updateFilm(long filmId, Film film) {
        if (film == null) {
            throw new RepositoryException("Film must not be null");
        }

        return transactionManager.executeInTransaction(connection -> {
            String sql = """
                    UPDATE films
                    SET title = ?, year = ?, description = ?
                    WHERE film_id = ?
                    """;

            int updatedRows;

            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setString(1, film.title());
                statement.setInt(2, film.year());

                if (film.description() == null) {
                    statement.setNull(3, Types.VARCHAR);
                } else {
                    statement.setString(3, film.description());
                }

                statement.setLong(4, filmId);

                updatedRows = statement.executeUpdate();
            }

            if (updatedRows == 0) {
                throw new FilmNotFoundException(filmId);
            }

            replaceGenres(connection, filmId, film.genres());

            return findById(connection, filmId)
                    .orElseThrow(() ->
                            new RepositoryException(
                                    "Updated film could not be retrieved: " + filmId
                            ));
        });
    }

    // Deletes a film by its ID.
    // Foreign-key cascades remove associated genre relationships and screenings.
    @Override
    public boolean deleteFilmById(long filmId) {
        return withConnection(connection -> {
            String sql = "DELETE FROM films WHERE film_id = ?";

            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setLong(1, filmId);
                return statement.executeUpdate() > 0;
            }
        });
    }

    // Associates an existing genre with an existing film.
    // Returns false if the association already exists.
    @Override
    public boolean addGenreToFilm(long filmId, long genreId) {
        return transactionManager.executeInTransaction(connection -> {
            if (!filmExists(connection, filmId)) {
                throw new FilmNotFoundException(filmId);
            }

            if (!genreExists(connection, genreId)) {
                throw new GenreNotFoundException(genreId);
            }

            String checkSql = """
                SELECT 1
                FROM film_genres
                WHERE film_id = ? AND genre_id = ?
                """;

            try (PreparedStatement statement =
                         connection.prepareStatement(checkSql)) {

                statement.setLong(1, filmId);
                statement.setLong(2, genreId);

                try (ResultSet resultSet = statement.executeQuery()) {
                    if (resultSet.next()) {
                        return false;
                    }
                }
            }

            String insertSql = """
                INSERT INTO film_genres (film_id, genre_id)
                VALUES (?, ?)
                """;

            try (PreparedStatement statement =
                         connection.prepareStatement(insertSql)) {

                statement.setLong(1, filmId);
                statement.setLong(2, genreId);

                return statement.executeUpdate() > 0;
            }
        });
    }

    // Removes a genre association from an existing film.
    // Returns false if the association does not exist.
    @Override
    public boolean removeGenreFromFilm(long filmId, long genreId) {
        return transactionManager.executeInTransaction(connection -> {
            if (!filmExists(connection, filmId)) {
                throw new FilmNotFoundException(filmId);
            }

            if (!genreExists(connection, genreId)) {
                throw new GenreNotFoundException(genreId);
            }

            String sql = """
                DELETE FROM film_genres
                WHERE film_id = ? AND genre_id = ?
                """;

            try (PreparedStatement statement =
                         connection.prepareStatement(sql)) {

                statement.setLong(1, filmId);
                statement.setLong(2, genreId);

                return statement.executeUpdate() > 0;
            }
        });
    }

    // Retrieves one film and its genres using LEFT JOINs.
    // LEFT JOIN ensures films without genres are also returned.
    private Optional<Film> findById(Connection connection, long filmId)
            throws SQLException {

        String sql = """
                SELECT f.film_id, f.title, f.year, f.description, f.created_at,
                       g.genre_id, g.name AS genre_name
                FROM films f
                LEFT JOIN film_genres fg ON f.film_id = fg.film_id
                LEFT JOIN genres g ON fg.genre_id = g.genre_id
                WHERE f.film_id = ?
                ORDER BY g.genre_id
                """;

        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, filmId);

            try (ResultSet resultSet = statement.executeQuery()) {
                Map<Long, FilmBuilder> films = mapFilms(resultSet);

                return films.values().stream()
                        .findFirst()
                        .map(FilmBuilder::build);
            }
        }
    }

    // Counts all films currently stored in the database.
    // The count is used to calculate pagination metadata.
    private long countFilms(Connection connection) throws SQLException {
        String sql = "SELECT COUNT(*) FROM films";

        try (Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery(sql)) {
            resultSet.next();
            return resultSet.getLong(1);
        }
    }

    // Retrieves a page of films before joining their genre associations.
    // This prevents multiple genre rows from affecting the requested page size.
    private List<Film> findPage(Connection connection, int page, int perPage)
            throws SQLException {

        String sql = """
                SELECT f.film_id, f.title, f.year, f.description, f.created_at,
                       g.genre_id, g.name AS genre_name
                FROM (
                    SELECT film_id, title, year, description, created_at
                    FROM films
                    ORDER BY film_id
                    LIMIT ? OFFSET ?
                ) f
                LEFT JOIN film_genres fg ON f.film_id = fg.film_id
                LEFT JOIN genres g ON fg.genre_id = g.genre_id
                ORDER BY f.film_id, g.genre_id
                """;

        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, perPage);
            statement.setInt(2, Page.offset(page, perPage));

            try (ResultSet resultSet = statement.executeQuery()) {
                return mapFilms(resultSet).values().stream()
                        .map(FilmBuilder::build)
                        .toList();
            }
        }
    }

    // Validates pagination arguments consistently across film retrieval methods.
    private void validatePagination(int page, int perPage) {
        if (page < 1 || perPage < 1) {
            throw new RepositoryException(
                    "Page and perPage must both be greater than zero"
            );
        }
    }

    // Counts films matching a supplied WHERE clause.
    private long countFilteredFilms(
            Connection connection,
            String whereClause,
            SqlBinder binder
    ) throws SQLException {

        String sql = "SELECT COUNT(*) FROM films f WHERE " + whereClause;

        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            binder.bind(statement);

            try (ResultSet resultSet = statement.executeQuery()) {
                resultSet.next();
                return resultSet.getLong(1);
            }
        }
    }

    // Retrieves a page of matching films, including their associated genres.
    // Pagination happens before joining genres so each film occupies one page slot.
    private List<Film> findFilteredPage(
            Connection connection,
            String whereClause,
            SqlBinder binder,
            int page,
            int perPage
    ) throws SQLException {

        String sql = """
            SELECT f.film_id, f.title, f.year, f.description, f.created_at,
                   g.genre_id, g.name AS genre_name
            FROM (
                SELECT f.film_id, f.title, f.year, f.description, f.created_at
                FROM films f
                WHERE %s
                ORDER BY f.film_id
                LIMIT ? OFFSET ?
            ) f
            LEFT JOIN film_genres fg ON f.film_id = fg.film_id
            LEFT JOIN genres g ON fg.genre_id = g.genre_id
            ORDER BY f.film_id, g.genre_id
            """.formatted(whereClause);

        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            binder.bind(statement);

            statement.setInt(2, perPage);
            statement.setInt(3, Page.offset(page, perPage));

            try (ResultSet resultSet = statement.executeQuery()) {
                return mapFilms(resultSet).values().stream()
                        .map(FilmBuilder::build)
                        .toList();
            }
        }
    }

    // Checks whether a film exists.
    private boolean filmExists(Connection connection, long filmId)
            throws SQLException {

        String sql = "SELECT 1 FROM films WHERE film_id = ?";

        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, filmId);

            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next();
            }
        }
    }

    // Checks whether a genre exists.
    private boolean genreExists(Connection connection, long genreId)
            throws SQLException {

        String sql = "SELECT 1 FROM genres WHERE genre_id = ?";

        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, genreId);

            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next();
            }
        }
    }

    // Groups joined result-set rows into individual films and their genres.
    // LinkedHashMap preserves the ordering established by the SQL query.
    private Map<Long, FilmBuilder> mapFilms(ResultSet resultSet)
            throws SQLException {

        Map<Long, FilmBuilder> films = new LinkedHashMap<>();

        while (resultSet.next()) {
            long filmId = resultSet.getLong("film_id");

            FilmBuilder film = films.get(filmId);

            if (film == null) {
                film = new FilmBuilder(
                        filmId,
                        resultSet.getString("title"),
                        resultSet.getInt("year"),
                        resultSet.getString("description"),
                        resultSet.getString("created_at")
                );

                films.put(filmId, film);
            }

            long genreId = resultSet.getLong("genre_id");

            if (!resultSet.wasNull()) {
                film.genres.add(new Genre(
                        genreId,
                        resultSet.getString("genre_name")
                ));
            }
        }

        return films;
    }

    // Associates existing genres with a film using a batch insert.
    // Checks genre IDs first so missing references produce GenreNotFoundException.
    private void insertGenres(
            Connection connection,
            long filmId,
            List<Genre> genres
    ) throws SQLException {

        String checkSql = "SELECT 1 FROM genres WHERE genre_id = ?";
        String insertSql = """
                INSERT INTO film_genres (film_id, genre_id)
                VALUES (?, ?)
                """;

        try (PreparedStatement checkStatement = connection.prepareStatement(checkSql);
             PreparedStatement insertStatement = connection.prepareStatement(insertSql)) {

            for (Genre genre : genres) {
                if (genre == null || genre.genreId() == null) {
                    throw new RepositoryException(
                            "Every film genre must have a persisted genre ID"
                    );
                }

                long genreId = genre.genreId();

                checkStatement.setLong(1, genreId);

                try (ResultSet resultSet = checkStatement.executeQuery()) {
                    if (!resultSet.next()) {
                        throw new GenreNotFoundException(genreId);
                    }
                }

                insertStatement.setLong(1, filmId);
                insertStatement.setLong(2, genreId);
                insertStatement.addBatch();
            }

            insertStatement.executeBatch();
        }
    }

    // Removes a film's existing genre associations and inserts their replacements.
    // The calling update operation wraps both actions in the same transaction.
    private void replaceGenres(
            Connection connection,
            long filmId,
            List<Genre> genres) throws SQLException {

        String deleteSql = "DELETE FROM film_genres WHERE film_id = ?";

        try (PreparedStatement statement = connection.prepareStatement(deleteSql)) {
            statement.setLong(1, filmId);
            statement.executeUpdate();
        }

        insertGenres(connection, filmId, genres);
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

    @FunctionalInterface
    private interface SqlBinder {
        void bind(PreparedStatement statement) throws SQLException;
    }

    // Accumulates a film's database fields and associated genres while mapping query results.
    // Builds the immutable Film record once all rows of that film have been processed.
    private static final class FilmBuilder {

        private final long filmId;
        private final String title;
        private final int year;
        private final String description;
        private final String createdAt;
        private final List<Genre> genres = new ArrayList<>();

        private FilmBuilder(
                long filmId,
                String title,
                int year,
                String description,
                String createdAt
        ) {
            this.filmId = filmId;
            this.title = title;
            this.year = year;
            this.description = description;
            this.createdAt = createdAt;
        }

        private Film build() {
            return new Film(
                    filmId, title, year, description, createdAt, genres
            );
        }
    }
}