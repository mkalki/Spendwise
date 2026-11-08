package com.spendwise.auth.dto;

public record AuthResponse(
        String id,
        String email,
        String username,
        String name,
        String pictureUrl,
        String provider) {
}
