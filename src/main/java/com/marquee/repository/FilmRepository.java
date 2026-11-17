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
}
