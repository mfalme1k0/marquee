# Marquee

A shared film screening catalogue exposed as an HTTP + JSON API. One database,
many readers and writers, no more spreadsheets floating between laptops.

**Stack:** Java 21, Maven, `com.sun.net.httpserver` (no framework), SQLite via JDBC (no ORM).

## Getting started

Requirements: JDK 21+, Maven 3.9+.

```bash
mvn -B verify                 # build + run tests
java -jar target/marquee.jar  # start the server on :8080 (set PORT to change)
curl http://localhost:8080/films
```

The database file is created and the schema applied on first run. To load sample data:

```bash
sqlite3 marquee.db < db/seed.sql
```

## Architecture

```
handler  ->  service  ->  repository  ->  SQLite
(HTTP)       (rules)       (SQL)
```

Each layer only calls the one below it. See the `package-info.java` in each package.

```
src/main/java/com/marquee/
  handler/     HTTP in/out
  service/     validation and business rules
  repository/  SQL
  model/       shared data types
  http/        router, JSON parsing, error envelope
  db/          connection + schema bootstrap
src/main/resources/schema.sql
db/seed.sql
api-tests/     API test collection
docs/          API reference, design decisions, ER diagram
```

## Documentation

- [API reference](docs/api.md)
- [Design decisions](docs/design-decisions.md)
- [ER diagram](docs/er-diagram.md)
- [Test collection](api-tests/README.md)

## Team

| Name | Role | GitHub |
|------|------|--------|
| Fidel Shikokoti | Project lead | @CHANGE-ME-fidel |
| Francis Kimani | Member | @CHANGE-ME-francis |
| Jackson Macharia | Member | @CHANGE-ME-jackson |

## Contributing

Read [CONTRIBUTING.md](CONTRIBUTING.md). Short version: branch, small commits,
pull request, one approving review, green CI, merge.
