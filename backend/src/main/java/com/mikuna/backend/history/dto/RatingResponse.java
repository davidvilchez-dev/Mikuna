package com.mikuna.backend.history.dto;

import com.mikuna.backend.history.model.Rating;
import java.time.Instant;
import java.util.UUID;

public record RatingResponse(UUID orderId, int value, Instant createdAt) {

    public static RatingResponse of(Rating rating) {
        return new RatingResponse(rating.getOrderId(), rating.getValue(), rating.getCreatedAt());
    }
}
