package com.engineeringproductivity.platform.auth.api;

import java.util.UUID;

public record AuthResponse(
        String accessToken,
        String refreshToken,
        String tokenType,
        String role,
        UUID userId
) {
    public AuthResponse(String accessToken, String refreshToken, String role, UUID userId) {
        this(accessToken, refreshToken, "Bearer", role, userId);
    }
}
