
package com.marquee.service;

import com.marquee.exceptions.DuplicateGenreException;
import com.marquee.exceptions.GenreNotFoundException;
import com.marquee.exceptions.ValidationException;
import com.marquee.model.Genre;
import com.marquee.repository.GenreRepository;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;


public final class GenreService {

    private static final int MAX_NAME_LENGTH = 100;

    private final GenreRepository genreRepository;

  //create genre service with required repos
    public GenreService(GenreRepository genreRepository) {
        this.genreRepository = Objects.requireNonNull(
                genreRepository,
                "GenreRepository must not be null"
        );
    }
//creates genre
    public Genre createGenre(Genre genre) {

        String name = validateAndNormalizeName(genre);

        Genre newGenre = new Genre(
                null,
                name
        );

        return genreRepository.createGenre(newGenre);
    }

 //retrieve a genre by id
    public Genre getGenreById(long genreId) {

        validateId(genreId);

        return genreRepository.findGenreById(genreId)
                .orElseThrow(() ->
                        new GenreNotFoundException(genreId)
                );
    }

 //retrieve all genres
    public List<Genre> getAllGenres() {
        return genreRepository.findAllGenres();
    }

  //update genre
    public Genre updateGenre(long genreId, Genre genre) {

        validateId(genreId);

        String name = validateAndNormalizeName(genre);

        Genre updatedGenre = new Genre(
                genreId,
                name
        );

        return genreRepository.updateGenre(
                genreId,
                updatedGenre
        );
    }

   //delete genre
    public boolean deleteGenre(long genreId) {

        validateId(genreId);

        return genreRepository.deleteGenreById(genreId);
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
