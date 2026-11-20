package com.marquee.repository;

import com.marquee.model.Film;
import com.marquee.model.Page;

import java.util.Optional;

public interface FilmRepository {

    Film createFilm(Film film);

    Optional<Film> findFilmById(long filmId);

    Page<Film> findAllFilms(int page, int perPage);

    Film updateFilm(long filmId, Film film);

    boolean deleteFilmById(long filmId);

    Page<Film> findFilmsByTitle(String title, int page, int perPage);

    Page<Film> findFilmsByYear(int year, int page, int perPage);

    Page<Film> findFilmsByGenreId(long genreId, int page, int perPage);

    boolean addGenreToFilm(long filmId, long genreId);

    boolean removeGenreFromFilm(long filmId, long genreId);
}