package com.knowledgeapplication.api.exception;

import java.util.Map;

public record ApiError(
        String code,
        String message,
        Map<String, String> fieldErrors
) {
    public static ApiError of(String code, String message) {
        return new ApiError(code, message, Map.of());
    }
}
