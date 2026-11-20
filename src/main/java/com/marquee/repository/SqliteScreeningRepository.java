package com.marquee.repository;

import com.marquee.db.transactions.ConnectionProvider;
import com.marquee.db.transactions.TransactionManager;
import com.marquee.exceptions.FilmNotFoundException;
import com.marquee.exceptions.RepositoryException;
import com.marquee.exceptions.ScreeningNotFoundException;
import com.marquee.model.Page;
import com.marquee.model.Screening;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class SqliteScreeningRepository implements ScreeningRepository {

    private final ConnectionProvider connectionProvider;
    private final TransactionManager transactionManager;

    // Creates a screening repository using the connection provider and transaction manager.
    public SqliteScreeningRepository(
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

    // Creates a screening and returns it with its generated database ID.
    @Override
    public Screening createScreening(Screening screening) {
        if (screening == null) {
            throw new RepositoryException("Screening must not be null");
        }

        if (screening.filmId() == null) {
            throw new RepositoryException("Screening film ID must not be null");
        }

        return transactionManager.executeInTransaction(connection -> {
            String sql = """
                    INSERT INTO screenings (film_id, venue, screening_time)
                    SELECT film_id, ?, ?
                    FROM films
                    WHERE film_id = ?
                    """;

            try {
                try (PreparedStatement statement = connection.prepareStatement(sql)) {
                    statement.setString(1, screening.venue());
                    statement.setString(2, screening.screeningTime());
                    statement.setLong(3, screening.filmId());

                    if (statement.executeUpdate() == 0) {
                        throw new FilmNotFoundException(screening.filmId());
                    }
                }

                try (PreparedStatement idStatement = connection.prepareStatement(
                        "SELECT last_insert_rowid()");
                     ResultSet resultSet = idStatement.executeQuery()) {

                    if (!resultSet.next()) {
                        throw new RepositoryException(
                                "Failed to retrieve the generated screening ID"
                        );
                    }

                    long screeningId = resultSet.getLong(1);

                    return findScreeningById(connection, screeningId);
                }

            } catch (SQLException exception) {
                throw new RepositoryException("Failed to create screening", exception);
            }
        });
    }

    // Retrieves a screening by its ID.
    // Returns an empty Optional when no screening matches the supplied ID.
    @Override
    public Optional<Screening> findScreeningById(long screeningId) {
        String sql = """
                SELECT s.screening_id, s.film_id, f.title AS film_title,
                       s.venue, s.screening_time
                FROM screenings s
                JOIN films f ON f.film_id = s.film_id
                WHERE s.screening_id = ?
                """;

        return withConnection("Failed to retrieve screening", connection -> {
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setLong(1, screeningId);

                try (ResultSet resultSet = statement.executeQuery()) {
                    if (!resultSet.next()) {
                        return Optional.empty();
                    }

                    return Optional.of(mapScreening(resultSet));
                }
            }
        });
    }

    // Retrieves a paginated list of all screenings.
    @Override
    public Page<Screening> findAllScreenings(int page, int perPage) {
        return findScreeningPage(
                "",
                statement -> {
                },
                page,
                perPage,
                "Failed to retrieve screenings"
        );
    }

    // Retrieves paginated screenings associated with the specified film.
    @Override
    public Page<Screening> findScreeningsByFilmId(
            long filmId, int page, int perPage) {

        return findScreeningPage(
                "WHERE s.film_id = ?",
                statement -> statement.setLong(1, filmId),
                page,
                perPage,
                "Failed to retrieve screenings for film " + filmId
        );
    }

    // Retrieves paginated screenings held at the specified venue.
    @Override
    public Page<Screening> findScreeningsByVenue(
            String venue, int page, int perPage) {

        if (venue == null || venue.isBlank()) {
            throw new RepositoryException("Venue must not be blank");
        }

        return findScreeningPage(
                "WHERE s.venue = ?",
                statement -> statement.setString(1, venue),
                page,
                perPage,
                "Failed to retrieve screenings for venue " + venue
        );
    }

    // Retrieves paginated screenings scheduled on the specified date.
    @Override
    public Page<Screening> findScreeningsByDate(
            LocalDate date, int page, int perPage) {

        if (date == null) {
            throw new RepositoryException("Screening date must not be null");
        }

        String startDateTime = date + "T00:00:00";
        String endDateTime = date.plusDays(1) + "T00:00:00";

        return findScreeningPage(
                "WHERE s.screening_time >= ? AND s.screening_time < ?",
                statement -> {
                    statement.setString(1, startDateTime);
                    statement.setString(2, endDateTime);
                },
                page,
                perPage,
                "Failed to retrieve screenings for date " + date
        );
    }

    // Updates an existing screening.
    // Throws FilmNotFoundException when the supplied film ID does not exist.
    @Override
    public Screening updateScreening(long screeningId, Screening screening) {
        if (screening == null) {
            throw new RepositoryException("Screening must not be null");
        }

        if (screening.filmId() == null) {
            throw new RepositoryException("Screening film ID must not be null");
        }

        return transactionManager.executeInTransaction(connection -> {
            String sql = """
                    UPDATE screenings
                    SET film_id = ?, venue = ?, screening_time = ?
                    WHERE screening_id = ?
                    """;

            try {
                if (!screeningExists(connection, screeningId)) {
                    throw new ScreeningNotFoundException(screeningId);
                }

                try (PreparedStatement filmStatement = connection.prepareStatement(
                        "SELECT EXISTS (SELECT 1 FROM films WHERE film_id = ?)")) {

                    filmStatement.setLong(1, screening.filmId());

                    try (ResultSet resultSet = filmStatement.executeQuery()) {
                        if (!resultSet.getBoolean(1)) {
                            throw new FilmNotFoundException(screening.filmId());
                        }
                    }
                }

                try (PreparedStatement statement = connection.prepareStatement(sql)) {
                    statement.setLong(1, screening.filmId());
                    statement.setString(2, screening.venue());
                    statement.setString(3, screening.screeningTime());
                    statement.setLong(4, screeningId);

                    statement.executeUpdate();
                }

                return findScreeningById(connection, screeningId);

            } catch (SQLException exception) {
                throw new RepositoryException("Failed to update screening", exception);
            }
        });
    }

    // Deletes a screening by its ID.
    @Override
    public boolean deleteScreeningById(long screeningId) {
        String sql = "DELETE FROM screenings WHERE screening_id = ?";

        return withConnection("Failed to delete screening", connection -> {
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setLong(1, screeningId);
                return statement.executeUpdate() > 0;
            }
        });
    }

    // Retrieves a consistent paginated screening result and its total count.
    private Page<Screening> findScreeningPage(
            String whereClause,
            SqlStatementBinder filterBinder,
            int page,
            int perPage,
            String errorMessage) {

        if (page < 1 || perPage < 1) {
            throw new RepositoryException("Page and perPage must be >= 1");
        }

        int offset = Page.offset(page, perPage);

        return transactionManager.executeInTransaction(connection -> {
            String countSql = """
                    SELECT COUNT(*)
                    FROM screenings s
                    """ + whereClause;

            String dataSql = """
                    SELECT s.screening_id, s.film_id, f.title AS film_title,
                           s.venue, s.screening_time
                    FROM screenings s
                    JOIN films f ON f.film_id = s.film_id
                    """ + whereClause + """
                    ORDER BY s.screening_time, s.screening_id
                    LIMIT ? OFFSET ?
                    """;

            try {
                long total;

                try (PreparedStatement countStatement =
                             connection.prepareStatement(countSql)) {

                    filterBinder.bind(countStatement);

                    try (ResultSet resultSet = countStatement.executeQuery()) {
                        resultSet.next();
                        total = resultSet.getLong(1);
                    }
                }

                List<Screening> screenings = new ArrayList<>();

                try (PreparedStatement dataStatement =
                             connection.prepareStatement(dataSql)) {

                    filterBinder.bind(dataStatement);

                    int parameterIndex = whereClause.isEmpty()
                            ? 1
                            : countParameters(whereClause) + 1;

                    dataStatement.setInt(parameterIndex, perPage);
                    dataStatement.setInt(parameterIndex + 1, offset);

                    try (ResultSet resultSet = dataStatement.executeQuery()) {
                        while (resultSet.next()) {
                            screenings.add(mapScreening(resultSet));
                        }
                    }
                }

                return new Page<>(page, perPage, total, screenings);

            } catch (SQLException exception) {
                throw new RepositoryException(errorMessage, exception);
            }
        });
    }

    // Counts the placeholders in the repository's fixed WHERE clauses.
    private int countParameters(String whereClause) {
        int count = 0;

        for (int i = 0; i < whereClause.length(); i++) {
            if (whereClause.charAt(i) == '?') {
                count++;
            }
        }

        return count;
    }

    // Retrieves a screening using an existing database connection.
    private Screening findScreeningById(Connection connection, long screeningId)
            throws SQLException {

        String sql = """
                SELECT s.screening_id, s.film_id, f.title AS film_title,
                       s.venue, s.screening_time
                FROM screenings s
                JOIN films f ON f.film_id = s.film_id
                WHERE s.screening_id = ?
                """;

        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, screeningId);

            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    throw new ScreeningNotFoundException(screeningId);
                }

                return mapScreening(resultSet);
            }
        }
    }

    // Checks whether a screening with the specified ID exists.
    private boolean screeningExists(Connection connection, long screeningId)
            throws SQLException {

        String sql = """
                SELECT EXISTS (
                    SELECT 1
                    FROM screenings
                    WHERE screening_id = ?
                )
                """;

        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, screeningId);

            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.getBoolean(1);
            }
        }
    }

    // Maps the current result-set row to a Screening record.
    private Screening mapScreening(ResultSet resultSet) throws SQLException {
        return new Screening(
                resultSet.getLong("screening_id"),
                resultSet.getLong("film_id"),
                resultSet.getString("film_title"),
                resultSet.getString("venue"),
                resultSet.getString("screening_time")
        );
    }

    // Executes a standalone database operation and closes its connection afterward.
    // Converts JDBC failures into the repository's unchecked exception hierarchy.
    private <T> T withConnection(String errorMessage, SqlFunction<T> operation) {
        try (Connection connection = connectionProvider.getConnection()) {
            return operation.apply(connection);
        } catch (SQLException exception) {
            throw new RepositoryException(errorMessage, exception);
        }
    }

    @FunctionalInterface
    private interface SqlFunction<T> {
        T apply(Connection connection) throws SQLException;
    }

    @FunctionalInterface
    private interface SqlStatementBinder {
        void bind(PreparedStatement statement) throws SQLException;
    }
}