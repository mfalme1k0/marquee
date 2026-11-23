# API Test Collection

The current handler layer is covered end-to-end by `src/test/java/com/marquee/handler/ApiIntegrationTest.java`.

## How to run
From the project root, run:

```bash
mvn -B -Dtest=com.marquee.handler.ApiIntegrationTest test
```

Run the complete test suite with `mvn -B verify`. The integration test starts an ephemeral HTTP server and a temporary SQLite database, so it does not require a manually running server or modify the developer's `marquee.db`.

For manual exploration, start the packaged app with `mvn -B package && java -jar target/marquee.jar`, then use `curl` against `http://localhost:8080`. For example:

```bash
curl -i 'http://localhost:8080/films?page=1&per_page=10'
curl -i 'http://localhost:8080/screenings?film_id=1'
curl -i 'http://localhost:8080/films?page=2&per_page=5'
curl -i -X POST http://localhost:8080/genres \
  -H 'Content-Type: application/json' -d '{"name":"Mystery"}'
```

## Must cover
- Films: create, read, update, delete, list and pagination
- Genres: create, list, read, update, delete, duplicate -> 409
- Screenings: create, read, update, delete, filter by film or venue; nonexistent film -> 404
- Pagination: default page size and query validation
- Errors: missing fields, wrong types, malformed JSON, wrong method (405), unknown route (404)
- Cascade: deleting a film removes its screenings and film_genres

The API integration tests in src/test/java/com/marquee/handler/ApiIntegrationTest.java exercise these routes against the current service and repository implementations. Keep this checklist aligned with that test suite when API behavior changes.