package com.knowledgeapplication.api.auth;

public record CsrfTokenResponse(
        String token,
        String headerName
) {
}
