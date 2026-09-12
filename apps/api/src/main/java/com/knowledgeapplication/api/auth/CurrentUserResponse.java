package com.knowledgeapplication.api.auth;

public record CurrentUserResponse(
        String email,
        String name,
        String picture
) {
}
