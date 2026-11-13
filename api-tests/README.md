# API Test Collection

Save the Apidog collection (or a curl script) in this folder.

## How to run
TODO: import steps / command.

## Must cover
- Film CRUD + filters (genre, year, title)
- Genres: create, list, delete, duplicate -> 409
- Film-genre associations: assign (201), remove (204), duplicate (409)
- Screening CRUD + filters, nonexistent film -> 404
- Pagination: multiple pages, default size, edge values
- Errors: missing fields, wrong types, malformed JSON, wrong method (405), unknown route (404)
- Cascade: deleting a film removes its screenings and film_genres
