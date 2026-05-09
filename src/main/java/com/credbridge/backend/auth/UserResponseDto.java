package com.credbridge.backend.auth;

import java.time.LocalDateTime;

public record UserResponseDto(
        Long id,
        String fullName,
        String email,
        UserRole role,
        LocalDateTime createdAt
) {
    static UserResponseDto from(User user) {
        return new UserResponseDto(
                user.getId(),
                user.getFullName(),
                user.getEmail(),
                user.getRole(),
                user.getCreatedAt()
        );
    }
}
