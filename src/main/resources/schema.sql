-- Applied on first run when the database is empty.
-- Remember: PRAGMA foreign_keys = ON must be set on EVERY connection.

CREATE TABLE IF NOT EXISTS films (
    film_id     INTEGER PRIMARY KEY AUTOINCREMENT,
    title       TEXT    NOT NULL,
    year        INTEGER NOT NULL CHECK (year > 0),
    description TEXT,
    created_at  TEXT    NOT NULL DEFAULT (strftime('%Y-%m-%dT%H:%M:%SZ', 'now'))
);

CREATE TABLE IF NOT EXISTS genres (
    genre_id INTEGER PRIMARY KEY AUTOINCREMENT,
    name     TEXT NOT NULL UNIQUE
);

CREATE TABLE IF NOT EXISTS film_genres (
    film_id  INTEGER NOT NULL REFERENCES films(film_id)   ON DELETE CASCADE,
    genre_id INTEGER NOT NULL REFERENCES genres(genre_id) ON DELETE CASCADE,
    PRIMARY KEY (film_id, genre_id)
);

CREATE TABLE IF NOT EXISTS screenings (
    screening_id   INTEGER PRIMARY KEY AUTOINCREMENT,
    film_id        INTEGER NOT NULL REFERENCES films(film_id) ON DELETE CASCADE,
    venue          TEXT    NOT NULL,
    screening_time TEXT    NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_screenings_film  ON screenings(film_id);
CREATE INDEX IF NOT EXISTS idx_screenings_venue ON screenings(venue);
CREATE INDEX IF NOT EXISTS idx_screenings_time  ON screenings(screening_time);
