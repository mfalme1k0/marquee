# API Reference

Base URL for local development: `http://localhost:8080`. All request and response bodies use JSON. List endpoints default to `page=1` and `per_page=10`; the services accept page sizes up to 100. Invalid paging values return `400`. Unknown and repeated query parameters are rejected.

Film titles must contain 1–255 non-whitespace characters and years must be at least 1888 on writes. Descriptions may be null and are limited to 5,000 characters. Genre names are required and limited to 100 characters. Screening venues are required and limited to 255 characters; screening timestamps must be ISO-8601 UTC values ending in `Z`.

Server-generated IDs and timestamps are not client-controlled. Extra ID, timestamp, and derived fields in request bodies do not replace values produced by the service/repository.

## Films

| Method and route | Behavior | Success | Other status codes |
|---|---|---|---|
| `POST /films` | Create a film. `title` and `year` are required; `description` and `genres` are optional. Genre entries must reference persisted IDs. | `201` full film | `400` invalid body/fields; `404` referenced genre not found |
| `GET /films` | List films; each list item omits `description` but includes genres. | `200` paginated `{page, per_page, total, films}` | `400` invalid pagination, unsupported parameter, or conflicting filters |
| `GET /films?title={text}` | Partial title search, paginated. | `200` paginated film list (zero matches is an empty page) | `400` blank title or invalid pagination |
| `GET /films?year={year}` | Exact year filter, paginated. | `200` paginated film list | `400` invalid year or pagination |
| `GET /films?genre={name}` | Filter by exact genre name, paginated. Unknown genre yields an empty page. | `200` paginated film list | `400` blank genre or invalid pagination |
| `GET /films?genre_id={id}` | Filter by genre ID, paginated. An unknown positive ID yields an empty page. | `200` paginated film list | `400` invalid ID or pagination |
| `POST /films/{filmId}/genres` | Assign genre using body `{ "genre_id": 1 }`. | `201` `{ "film_id": 1, "genre_id": 1 }` | `400` invalid ID/body; `404` film or genre not found; `409` already assigned |
| `DELETE /films/{filmId}/genres/{genreId}` | Remove an association. Repeated deletion is idempotent; absent association still returns `204`. | `204` no body | `400` invalid IDs; `404` film or genre not found |
| `GET /films/{filmId}` | Read full film detail, including description and genres. | `200` film object | `400` invalid ID; `404` film not found |
| `PUT /films/{filmId}` | Update a film. The service validates fields; the genres collection is replaced (omitted/null normalizes to empty). | `200` film object | `400` invalid fields; `404` film or referenced genre not found |
| `DELETE /films/{filmId}` | Delete a film; database cascade removes screenings and associations. | `204` no body | `400` invalid ID; `404` film not found |

Only one of `title`, `year`, `genre`, or `genre_id` may be supplied to `GET /films` at a time. Pagination parameters may accompany the selected filter.

## Genres

| Method and route | Behavior | Success | Other status codes |
|---|---|---|---|
| `POST /genres` | Create from `{ "name": "Drama" }`. | `201` genre object | `400` invalid name/body; `409` duplicate name |
| `GET /genres` | List genres. | `200` JSON array | `500` database failure |
| `GET /genres/{genreId}` | Read one genre. | `200` genre object | `400` invalid ID; `404` genre not found |
| `PUT /genres/{genreId}` | Update a genre name. | `200` updated genre | `400` invalid name/body; `404` missing genre; `409` duplicate name |
| `DELETE /genres/{genreId}` | Delete a genre; join-table rows are removed by the database. | `204` no body | `400` invalid ID; `404` genre not found |

## Screenings

| Method and route | Behavior | Success | Other status codes |
|---|---|---|---|
| `POST /screenings` | Create from `film_id`, `venue`, and `screening_time`. | `201` screening object | `400` invalid fields/body; `404` film not found |
| `GET /screenings` | List screenings with pagination; response is `{page, per_page, total, screenings}`. | `200` paginated list | `400` invalid pagination or conflicting/unsupported query |
| `GET /screenings?film_id={id}` | Filter by film ID, paginated. | `200` paginated list | `400` invalid ID/pagination; `404` film not found |
| `GET /screenings?venue={venue}` | Filter by exact venue, paginated. | `200` paginated list | `400` blank venue or invalid pagination |
| `GET /screenings?date=YYYY-MM-DD` | Filter by calendar date, paginated. Date must use `yyyy-MM-dd`. | `200` paginated list | `400` invalid date or pagination |
| `GET /screenings/{screeningId}` | Read one screening. | `200` screening object | `400` invalid ID; `404` screening not found |
| `PUT /screenings/{screeningId}` | Replace `film_id`, `venue`, and `screening_time`. | `200` updated screening | `400` invalid fields/body; `404` screening or film not found |
| `DELETE /screenings/{screeningId}` | Delete a screening. | `204` no body | `400` invalid ID; `404` screening not found |

Only one of `film_id`, `venue`, or `date` may be supplied to `GET /screenings` at a time; pagination may accompany it.

## Errors and routing

Every error uses a consistent envelope. `details` is included when present, such as for field validation failures.

```json
{"error":{"code":400,"message":"Validation failed","details":["Title is required"]}}
```

- `400` malformed JSON/query, invalid ID/pagination, conflicting filters, or service validation failure.
- `404` unknown route or a requested film, genre, screening, or referenced resource does not exist.
- `405` path exists but method is unsupported; response includes `Allow`.
- `409` duplicate genre name or repeated film-genre assignment.
- `500` unexpected persistence/server failure; internal details are logged, not exposed.
