package com.mikuna.backend.auth.dto;

import java.time.Instant;

public record LoginResponse(String accessToken, String tokenType, Instant expiresAt, AccountResponse account) {
}
