package com.marquee.handler;

import com.google.gson.annotations.SerializedName;

/** Minimal request DTO for POST /films/{filmId}/genres. */
public record GenreAssignmentRequest(@SerializedName("genre_id") Long genreId) {}
