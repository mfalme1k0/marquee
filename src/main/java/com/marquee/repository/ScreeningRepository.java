package com.marquee.repository;

import com.marquee.model.Page;
import com.marquee.model.Screening;

import java.time.LocalDate;
import java.util.Optional;

public interface ScreeningRepository {

    Screening createScreening(Screening screening);

    Optional<Screening> findScreeningById(long screeningId);

    Page<Screening> findAllScreenings(int page, int perPage);

    Page<Screening> findScreeningsByFilmId(long filmId, int page, int perPage);

    Page<Screening> findScreeningsByVenue(String venue, int page, int perPage);

    Page<Screening> findScreeningsByDate(LocalDate date, int page, int perPage);

    Screening updateScreening(long screeningId, Screening screening);

    boolean deleteScreeningById(long screeningId);
}