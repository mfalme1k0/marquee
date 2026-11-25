package com.marquee.repository;

import com.marquee.model.Genre;

import java.util.List;
import java.util.Optional;

public interface GenreRepository {
    Genre createGenre(Genre genre);

    Optional<Genre> findGenreById(long genreId);

    Optional<Genre> findGenreByName(String name);

    List<Genre> findAllGenres();

    Genre updateGenre(long genreId, Genre genre);

    boolean deleteGenreById(long genreId);
}