package com.credbridge.backend.common;

import java.time.LocalDateTime;

public record ApiErrorDto(
        int status,
        String error,
        String message,
        String path,
        LocalDateTime timestamp
) {
}
