package com.knowledgeapplication.api.auth;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.stereotype.Component;

import java.util.Locale;

@Component
public class OwnerIdentity {

    private final String allowedEmail;

    public OwnerIdentity(@Value("${app.auth.allowed-email}") String allowedEmail) {
        if (allowedEmail == null || allowedEmail.isBlank()) {
            throw new IllegalArgumentException("Allowed owner email must be configured");
        }
        this.allowedEmail = normalize(allowedEmail);
    }

    public boolean isAllowed(OidcUser principal) {
        return principal != null
                && Boolean.TRUE.equals(principal.getEmailVerified())
                && principal.getEmail() != null
                && allowedEmail.equals(normalize(principal.getEmail()));
    }

    public OidcUser requireAuthorized(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new AuthenticationCredentialsNotFoundException("Authentication is required");
        }
        if (!(authentication.getPrincipal() instanceof OidcUser principal) || !isAllowed(principal)) {
            throw new AccessDeniedException("Authenticated account is not authorized for this workspace");
        }
        return principal;
    }

    private static String normalize(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }
}
