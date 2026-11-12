/**
 * Database bootstrap: creates the SQLite file and tables on first run
 * (from {@code schema.sql}) and opens connections.
 *
 * <p><b>Important:</b> SQLite does NOT enforce foreign keys by default. Every
 * connection must run {@code PRAGMA foreign_keys = ON}, otherwise deleting a
 * film will not cascade to screenings and film_genres.
 */
package com.marquee.db;
