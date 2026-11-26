package com.marquee.service;

import com.marquee.exceptions.DuplicateGenreException;
import com.marquee.exceptions.GenreNotFoundException;
import com.marquee.exceptions.ValidationException;
import com.marquee.logging.AppLogger;
import com.marquee.model.Genre;
import com.marquee.repository.GenreRepository;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class GenreService {

    private static final int MAX_NAME_LENGTH = 100;

    private final GenreRepository genreRepository;
    private final AppLogger log;

    //create genre service with required repos
    public GenreService(GenreRepository genreRepository, AppLogger log) {
        this.genreRepository = Objects.requireNonNull(
                genreRepository,
                "GenreRepository must not be null"
        );
        this.log = Objects.requireNonNull(
                log,
                "AppLogger must not be null"
        );
    }

    //creates genre
    public Genre createGenre(Genre genre) {

        String name;

        try {
            name = validateAndNormalizeName(genre);
        } catch (ValidationException e) {
            log.warn(null, "genre.create.rejected", Map.of(
                    "reason", "validation_failed",
                    "details", String.valueOf(e.getMessage())
            ));
            throw e;
        }

        Genre newGenre = new Genre(
                null,
                name
        );

        Genre created = genreRepository.createGenre(newGenre);

        log.info(null, "genre.created", Map.of(
                "genre_id", created.genreId(),
                "name", created.name()
        ));

        return created;
    }

    //retrieve a genre by id
    public Genre getGenreById(long genreId) {

        validateId(genreId);

        return genreRepository.findGenreById(genreId)
                .orElseThrow(() -> {
                    log.warn(null, "genre.not_found", Map.of("genre_id", genreId));
                    return new GenreNotFoundException(genreId);
                });
    }

    //retrieve all genres
    public List<Genre> getAllGenres() {
        return genreRepository.findAllGenres();
    }

    //update genre
    public Genre updateGenre(long genreId, Genre genre) {

        String name;

        try {
            validateId(genreId);
            name = validateAndNormalizeName(genre);
        } catch (ValidationException e) {
            log.warn(null, "genre.update.rejected", Map.of(
                    "reason", "validation_failed",
                    "genre_id", genreId
            ));
            throw e;
        }

        Genre updatedGenre = new Genre(
                genreId,
                name
        );

        Genre updated = genreRepository.updateGenre(
                genreId,
                updatedGenre
        );

        log.info(null, "genre.updated", Map.of(
                "genre_id", updated.genreId(),
                "name", updated.name()
        ));

        return updated;
    }

    //delete genre
    public boolean deleteGenre(long genreId) {

        validateId(genreId);

        boolean deleted = genreRepository.deleteGenreById(genreId);

        if (deleted) {
            log.info(null, "genre.deleted", Map.of("genre_id", genreId));
        } else {
            log.warn(null, "genre.delete.not_found", Map.of("genre_id", genreId));
        }

        return deleted;
    }

    //validate and normalize genre
    private String validateAndNormalizeName(Genre genre) {

        if (genre == null) {
            throw new ValidationException(
                    List.of("Genre must not be null")
            );
        }

        List<String> errors = new ArrayList<>();

        String name = genre.name();

        if (name == null || name.isBlank()) {
            errors.add("Genre name is required");
        } else if (name.trim().length() > MAX_NAME_LENGTH) {
            errors.add(
                    "Genre name must not exceed "
                            + MAX_NAME_LENGTH
                            + " characters"
            );
        }

        if (!errors.isEmpty()) {
            throw new ValidationException(errors);
        }

        return name.trim();
    }

    //validate id
    private void validateId(long genreId) {

        if (genreId <= 0) {
            throw new ValidationException(
                    List.of("Genre ID must be greater than zero")
            );
        }
    }
}