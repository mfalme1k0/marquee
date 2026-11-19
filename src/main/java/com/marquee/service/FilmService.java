
package com.marquee.service;

import com.marquee.exceptions.FilmNotFoundException;
import com.marquee.exceptions.GenreNotFoundException;
import com.marquee.exceptions.ValidationException;
import com.marquee.model.Film;
import com.marquee.model.Genre;
import com.marquee.model.Page;
import com.marquee.repository.FilmRepository;
import com.marquee.repository.GenreRepository;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

public final class FilmService {

    private static final int MIN_YEAR = 1888;

    private final FilmRepository filmRepository;
    private final GenreRepository genreRepository;

   //creates film service with its required repos
    public FilmService(
            FilmRepository filmRepository,
            GenreRepository genreRepository
    ) {
        this.filmRepository = Objects.requireNonNull(
                filmRepository,
                "FilmRepository must not be null"
        );

        this.genreRepository = Objects.requireNonNull(
                genreRepository,
                "GenreRepository must not be null"
        );
    }


    public Film createFilm(Film film) {

        validateFilm(film);
        validateGenres(film.genres());

        Film newFilm = new Film(
                null,
                normalizeTitle(film.title()),
                film.year(),
                normalizeDescription(film.description()),
                null,
                film.genres()
        );

        return filmRepository.createFilm(newFilm);
    }

  //retrieve film using its id
    public Film getFilmById(long filmId) {

        validateId(filmId, "Film ID");

        return filmRepository.findFilmById(filmId)
                .orElseThrow(() ->
                        new FilmNotFoundException(filmId)
                );
    }

  //retrieve a list of films
    public Page<Film> getAllFilms(int page, int perPage) {

        List<String> errors = new ArrayList<>();

        if (page < 1) {
            errors.add("Page must be greater than zero");
        }

        if (perPage < 1) {
            errors.add("perPage must be greater than zero");
        }

        if (perPage > 100) {
            errors.add("perPage must not exceed 100");
        }

        if (!errors.isEmpty()) {
            throw new ValidationException(errors);
        }

        return filmRepository.findAllFilms(page, perPage);
    }

   //update an existing film
    public Film updateFilm(long filmId, Film film) {

        validateId(filmId, "Film ID");
        validateFilm(film);
        validateGenres(film.genres());

        Film updatedFilm = new Film(
                filmId,
                normalizeTitle(film.title()),
                film.year(),
                normalizeDescription(film.description()),
                null,
                film.genres()
        );

        return filmRepository.updateFilm(filmId, updatedFilm);
    }

   //delete a film
    public boolean deleteFilm(long filmId) {

        validateId(filmId, "Film ID");

        return filmRepository.deleteFilmById(filmId);
    }

//validate basic film information
    private void validateFilm(Film film) {

        if (film == null) {
            throw new ValidationException(
                    List.of("Film must not be null")
            );
        }

        List<String> errors = new ArrayList<>();

        if (film.title() == null || film.title().isBlank()) {
            errors.add("Title is required");
        } else if (film.title().trim().length() > 255) {
            errors.add("Title must not exceed 255 characters");
        }

        if (film.year() == null || film.year() < MIN_YEAR) {
            errors.add(
                    "Year must not be earlier than " + MIN_YEAR
            );
        }

        if (film.description() != null
                && film.description().length() > 5000) {
            errors.add(
                    "Description must not exceed 5000 characters"
            );
        }

        if (film.genres() == null) {
            errors.add("Genres must not be null");
        }

        if (!errors.isEmpty()) {
            throw new ValidationException(errors);
        }
    }


    private void validateGenres(List<Genre> genres) {

        if (genres == null) {
            return;
        }

        List<String> errors = new ArrayList<>();
        Set<Long> genreIds = new HashSet<>();

        for (Genre genre : genres) {

            if (genre == null) {
                errors.add("Genre must not be null");
                continue;
            }

            if (genre.genreId() == null || genre.genreId() <= 0) {
                errors.add(
                        "Every genre must have a valid persisted ID"
                );
                continue;
            }

            long genreId = genre.genreId();

            if (!genreIds.add(genreId)) {
                errors.add(
                        "Duplicate genre ID: " + genreId
                );
                continue;
            }

            if (genreRepository.findGenreById(genreId).isEmpty()) {
                throw new GenreNotFoundException(genreId);
            }
        }

        if (!errors.isEmpty()) {
            throw new ValidationException(errors);
        }
    }

    //validates positive database identifiers
    private void validateId(long id, String fieldName) {

        if (id <= 0) {
            throw new ValidationException(
                    List.of(fieldName + " must be greater than zero")
            );
        }
    }

   //removes whitespace
    private String normalizeTitle(String title) {
        return title.trim();
    }

 //nulls blank descriptions
    private String normalizeDescription(String description) {

        if (description == null || description.isBlank()) {
            return null;
        }

        return description.trim();
    }
}
