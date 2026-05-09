package com.credbridge.backend.auth;

public record LoginResponseDto(
        UserResponseDto user,
        String tokenType,
        String token
) {
}
