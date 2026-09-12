package com.knowledgeapplication.api.auth;

import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OwnerIdentityTest {

    private final OwnerIdentity ownerIdentity = new OwnerIdentity(" Owner@Example.com ");

    @Test
    void acceptsOnlyCaseInsensitiveVerifiedAllowlistedEmail() {
        var owner = authentication("owner@example.COM", true);

        assertThat(ownerIdentity.requireAuthorized(owner)).isSameAs(owner.getPrincipal());
        assertThat(ownerIdentity.isAllowed((DefaultOidcUser) owner.getPrincipal())).isTrue();
    }

    @Test
    void rejectsMissingAuthenticationWrongAccountAndUnverifiedEmail() {
        assertThatThrownBy(() -> ownerIdentity.requireAuthorized(null))
                .isInstanceOf(AuthenticationCredentialsNotFoundException.class);
        assertThatThrownBy(() -> ownerIdentity.requireAuthorized(authentication("other@example.com", true)))
                .isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> ownerIdentity.requireAuthorized(authentication("owner@example.com", false)))
                .isInstanceOf(AccessDeniedException.class);
    }

    private static OAuth2AuthenticationToken authentication(String email, boolean verified) {
        Instant now = Instant.now();
        var idToken = new OidcIdToken(
                "test-token",
                now.minusSeconds(30),
                now.plusSeconds(300),
                Map.of("sub", "subject", "email", email, "email_verified", verified)
        );
        var principal = new DefaultOidcUser(
                List.of(new SimpleGrantedAuthority(SecurityConfiguration.OWNER_AUTHORITY)),
                idToken
        );
        return new OAuth2AuthenticationToken(principal, principal.getAuthorities(), "google");
    }
}
