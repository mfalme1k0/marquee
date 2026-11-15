package com.marquee.exceptions;

public final class FilmNotFoundException extends RepositoryException {

    public FilmNotFoundException(long filmId) {
        super("Film " + filmId + " not found.");
    }
}