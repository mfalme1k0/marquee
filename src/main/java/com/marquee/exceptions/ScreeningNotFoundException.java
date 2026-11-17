package com.marquee.exceptions;

public final class ScreeningNotFoundException extends RepositoryException {

    public ScreeningNotFoundException(long screeningId) {
        super("Screening not found: " + screeningId);
    }
}
