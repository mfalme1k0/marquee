package com.marquee.exceptions;

public final class DuplicateGenreException extends RepositoryException {

    public DuplicateGenreException(String genreName) {
        super("Genre already exists: " + genreName);
    }
}
