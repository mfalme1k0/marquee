package com.marquee.service;

import com.marquee.exceptions.FilmNotFoundException;
import com.marquee.exceptions.ScreeningNotFoundException;
import com.marquee.exceptions.ValidationException;
import com.marquee.logging.AppLogger;
import com.marquee.model.Page;
import com.marquee.model.Screening;
import com.marquee.repository.FilmRepository;
import com.marquee.repository.ScreeningRepository;

import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class ScreeningService {

    private static final int MAX_VENUE_LENGTH = 255;
    private static final int MAX_PER_PAGE = 100;

    private final ScreeningRepository screeningRepository;
    private final FilmRepository filmRepository;
    private final AppLogger log;

    //create screening service with the required repos
    public ScreeningService(
            ScreeningRepository screeningRepository,
            FilmRepository filmRepository,
            AppLogger log
    ) {
        this.screeningRepository = Objects.requireNonNull(
                screeningRepository,
                "ScreeningRepository must not be null"
        );

        this.filmRepository = Objects.requireNonNull(
                filmRepository,
                "FilmRepository must not be null"
        );

        this.log = Objects.requireNonNull(
                log,
                "AppLogger must not be null"
        );
    }

    //create a new screening
    public Screening createScreening(Screening screening) {

        try {
            validateScreening(screening);
            validateFilmExists(screening.filmId());
        } catch (ValidationException | FilmNotFoundException e) {
            log.warn(null, "screening.create.rejected", Map.of(
                    "reason", e.getClass().getSimpleName(),
                    "film_id", String.valueOf(screening != null ? screening.filmId() : null)
            ));
            throw e;
        }

        Screening newScreening = new Screening(
                null,
                screening.filmId(),
                null,
                normalizeVenue(screening.venue()),
                normalizeTimestamp(screening.screeningTime())
        );

        Screening created = screeningRepository.createScreening(newScreening);

        log.info(null, "screening.created", Map.of(
                "screening_id", created.screeningId(),
                "film_id", created.filmId(),
                "venue", created.venue()
        ));

        return created;
    }

    //retrieve screening by id
    public Screening getScreeningById(long screeningId) {

        validateId(screeningId, "Screening ID");

        return screeningRepository.findScreeningById(screeningId)
                .orElseThrow(() -> {
                    log.warn(null, "screening.not_found", Map.of("screening_id", screeningId));
                    return new ScreeningNotFoundException(screeningId);
                });
    }

    //get a page of screenings
    public Page<Screening> getAllScreenings(int page, int perPage) {

        validatePagination(page, perPage);

        return screeningRepository.findAllScreenings(page, perPage);
    }

    //get a page of screenings of a film
    public Page<Screening> getScreeningsByFilmId(
            long filmId,
            int page,
            int perPage
    ) {

        validateId(filmId, "Film ID");
        validatePagination(page, perPage);

        try {
            validateFilmExists(filmId);
        } catch (FilmNotFoundException e) {
            log.warn(null, "screening.list_by_film.rejected", Map.of(
                    "reason", "film_not_found",
                    "film_id", filmId
            ));
            throw e;
        }

        return screeningRepository.findScreeningsByFilmId(
                filmId,
                page,
                perPage
        );
    }

    //get a page of screenings of a particular venue
    public Page<Screening> getScreeningsByVenue(
            String venue,
            int page,
            int perPage
    ) {

        String normalizedVenue = validateAndNormalizeVenue(venue);
        validatePagination(page, perPage);

        return screeningRepository.findScreeningsByVenue(
                normalizedVenue,
                page,
                perPage
        );
    }

    //get a page of screenings on a given date (yyyy-MM-dd)
    public Page<Screening> getScreeningsByDate(
            String date,
            int page,
            int perPage
    ) {

        LocalDate parsedDate = validateAndParseDate(date);
        validatePagination(page, perPage);

        return screeningRepository.findScreeningsByDate(
                parsedDate,
                page,
                perPage
        );
    }

    //update existing screening
    public Screening updateScreening(
            long screeningId,
            Screening screening
    ) {

        try {
            validateId(screeningId, "Screening ID");
            validateScreening(screening);
            validateFilmExists(screening.filmId());
        } catch (ValidationException | FilmNotFoundException e) {
            log.warn(null, "screening.update.rejected", Map.of(
                    "reason", e.getClass().getSimpleName(),
                    "screening_id", screeningId,
                    "film_id", String.valueOf(screening != null ? screening.filmId() : null)
            ));
            throw e;
        }

        Screening updatedScreening = new Screening(
                screeningId,
                screening.filmId(),
                null,
                normalizeVenue(screening.venue()),
                normalizeTimestamp(screening.screeningTime())
        );

        Screening updated = screeningRepository.updateScreening(
                screeningId,
                updatedScreening
        );

        log.info(null, "screening.updated", Map.of(
                "screening_id", updated.screeningId(),
                "film_id", updated.filmId()
        ));

        return updated;
    }

    //delete a screening
    public boolean deleteScreening(long screeningId) {

        validateId(screeningId, "Screening ID");

        boolean deleted = screeningRepository.deleteScreeningById(screeningId);

        if (deleted) {
            log.info(null, "screening.deleted", Map.of("screening_id", screeningId));
        } else {
            log.warn(null, "screening.delete.not_found", Map.of("screening_id", screeningId));
        }

        return deleted;
    }

    //validate required fields
    private void validateScreening(Screening screening) {

        if (screening == null) {
            throw new ValidationException(
                    List.of("Screening must not be null")
            );
        }

        List<String> errors = new ArrayList<>();

        if (screening.filmId() == null || screening.filmId() <= 0) {
            errors.add("Film ID must be greater than zero");
        }

        if (screening.venue() == null || screening.venue().isBlank()) {
            errors.add("Venue is required");
        } else if (screening.venue().trim().length()
                > MAX_VENUE_LENGTH) {
            errors.add(
                    "Venue must not exceed "
                            + MAX_VENUE_LENGTH
                            + " characters"
            );
        }

        if (screening.screeningTime() == null
                || screening.screeningTime().isBlank()) {
            errors.add("Screening time is required");
        } else if (!isValidUtcTimestamp(screening.screeningTime())) {
            errors.add(
                    "Screening time must be a valid ISO-8601 UTC timestamp"
            );
        }

        if (!errors.isEmpty()) {
            throw new ValidationException(errors);
        }
    }

    //checks if referenced film exists
    private void validateFilmExists(long filmId) {

        if (filmRepository.findFilmById(filmId).isEmpty()) {
            throw new FilmNotFoundException(filmId);
        }
    }


    private String validateAndNormalizeVenue(String venue) {

        if (venue == null || venue.isBlank()) {
            throw new ValidationException(
                    List.of("Venue is required")
            );
        }

        String normalizedVenue = venue.trim();

        if (normalizedVenue.length() > MAX_VENUE_LENGTH) {
            throw new ValidationException(
                    List.of(
                            "Venue must not exceed "
                                    + MAX_VENUE_LENGTH
                                    + " characters"
                    )
            );
        }

        return normalizedVenue;
    }

    //removes leading and trailing whitespaces
    private String normalizeVenue(String venue) {
        return venue.trim();
    }

    //validate utc timestamp
    private boolean isValidUtcTimestamp(String timestamp) {

        try {
            Instant.parse(timestamp);
            return timestamp.endsWith("Z");
        } catch (DateTimeParseException exception) {
            return false;
        }
    }

    //normalize a valid utc timestamp
    private String normalizeTimestamp(String timestamp) {
        return Instant.parse(timestamp).toString();
    }

    //validates page and page size
    private void validatePagination(int page, int perPage) {

        List<String> errors = new ArrayList<>();

        if (page < 1) {
            errors.add("Page must be greater than zero");
        }

        if (perPage < 1) {
            errors.add("perPage must be greater than zero");
        }

        if (perPage > MAX_PER_PAGE) {
            errors.add("perPage must not exceed " + MAX_PER_PAGE);
        }

        if (!errors.isEmpty()) {
            throw new ValidationException(errors);
        }
    }

    //parses a date filter in yyyy-MM-dd format
    private LocalDate validateAndParseDate(String date) {

        if (date == null || date.isBlank()) {
            throw new ValidationException(
                    List.of("Date is required")
            );
        }

        try {
            return LocalDate.parse(date.trim());
        } catch (DateTimeParseException exception) {
            throw new ValidationException(
                    List.of("Date must be in yyyy-MM-dd format")
            );
        }
    }

    //validates positive database identifier
    private void validateId(long id, String fieldName) {

        if (id <= 0) {
            throw new ValidationException(
                    List.of(fieldName + " must be greater than zero")
            );
        }
    }
}