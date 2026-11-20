package com.marquee.repository;

import com.marquee.model.Screening;

import java.util.List;
import java.util.Optional;

public interface ScreeningRepository {
    Screening createScreening(Screening screening);

    Optional<Screening> findScreeningById(long screeningId);

    List<Screening> findAllScreenings();

    List<Screening> findScreeningsByFilmId(long filmId);

    List<Screening> findScreeningsByVenue(String venue);

    Screening updateScreening(long screeningId, Screening screening);

    boolean deleteScreeningById(long screeningId);
}