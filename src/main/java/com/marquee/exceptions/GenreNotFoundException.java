package com.marquee.exceptions;

public final class GenreNotFoundException extends RepositoryException {

    public GenreNotFoundException(long genreId) {
        super("Genre not found: " + genreId);
    }
}