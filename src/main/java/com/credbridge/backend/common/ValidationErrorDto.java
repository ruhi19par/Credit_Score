package com.credbridge.backend.common;

import java.time.LocalDateTime;
import java.util.Map;

public record ValidationErrorDto(
        int status,
        String error,
        String message,
        String path,
        LocalDateTime timestamp,
        Map<String, String> fieldErrors
) {
}
