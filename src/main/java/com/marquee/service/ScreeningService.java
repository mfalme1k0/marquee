
package com.marquee.service;

import com.marquee.exceptions.FilmNotFoundException;
import com.marquee.exceptions.ScreeningNotFoundException;
import com.marquee.exceptions.ValidationException;
import com.marquee.model.Screening;
import com.marquee.repository.FilmRepository;
import com.marquee.repository.ScreeningRepository;

import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;


public final class ScreeningService {

    private static final int MAX_VENUE_LENGTH = 255;

    private final ScreeningRepository screeningRepository;
    private final FilmRepository filmRepository;

   //create screening service with the required repos
    public ScreeningService(
            ScreeningRepository screeningRepository,
            FilmRepository filmRepository
    ) {
        this.screeningRepository = Objects.requireNonNull(
                screeningRepository,
                "ScreeningRepository must not be null"
        );

        this.filmRepository = Objects.requireNonNull(
                filmRepository,
                "FilmRepository must not be null"
        );
    }

   //create a new screening
    public Screening createScreening(Screening screening) {

        validateScreening(screening);
        validateFilmExists(screening.filmId());

        Screening newScreening = new Screening(
                null,
                screening.filmId(),
                null,
                normalizeVenue(screening.venue()),
                normalizeTimestamp(screening.screeningTime())
        );

        return screeningRepository.createScreening(newScreening);
    }

 //retrieve screening by id
    public Screening getScreeningById(long screeningId) {

        validateId(screeningId, "Screening ID");

        return screeningRepository.findScreeningById(screeningId)
                .orElseThrow(() ->
                        new ScreeningNotFoundException(screeningId)
                );
    }

   //get all screenings
    public List<Screening> getAllScreenings() {
        return screeningRepository.findAllScreenings();
    }

   //get screenings of a film
    public List<Screening> getScreeningsByFilmId(long filmId) {

        validateId(filmId, "Film ID");
        validateFilmExists(filmId);

        return screeningRepository.findScreeningsByFilmId(filmId);
    }

 //get screenings of a particular venue
    public List<Screening> getScreeningsByVenue(String venue) {

        String normalizedVenue = validateAndNormalizeVenue(venue);

        return screeningRepository.findScreeningsByVenue(
                normalizedVenue
        );
    }

   //update existing screening
    public Screening updateScreening(
            long screeningId,
            Screening screening
    ) {

        validateId(screeningId, "Screening ID");
        validateScreening(screening);
        validateFilmExists(screening.filmId());

        Screening updatedScreening = new Screening(
                screeningId,
                screening.filmId(),
                null,
                normalizeVenue(screening.venue()),
                normalizeTimestamp(screening.screeningTime())
        );

        return screeningRepository.updateScreening(
                screeningId,
                updatedScreening
        );
    }

   //delete a screening
    public boolean deleteScreening(long screeningId) {

        validateId(screeningId, "Screening ID");

        return screeningRepository.deleteScreeningById(screeningId);
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
    private void validateFilmExists(Long filmId) {

        if (filmId == null || filmId <= 0) {
            throw new ValidationException(
                    List.of("Film ID must be greater than zero")
            );
        }

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

   //validates positive database identifier
    private void validateId(long id, String fieldName) {

        if (id <= 0) {
            throw new ValidationException(
                    List.of(fieldName + " must be greater than zero")
            );
        }
    }
}
