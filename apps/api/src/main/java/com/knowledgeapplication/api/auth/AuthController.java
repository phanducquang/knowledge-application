package com.knowledgeapplication.api.auth;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final OwnerIdentity ownerIdentity;

    public AuthController(OwnerIdentity ownerIdentity) {
        this.ownerIdentity = ownerIdentity;
    }

    @GetMapping("/me")
    public CurrentUserResponse me(Authentication authentication) {
        var principal = ownerIdentity.requireAuthorized(authentication);
        return new CurrentUserResponse(
                principal.getEmail(),
                principal.getFullName(),
                principal.getPicture()
        );
    }

    @GetMapping("/csrf")
    public CsrfTokenResponse csrf(HttpServletRequest request) {
        CsrfToken token = (CsrfToken) request.getAttribute(CsrfToken.class.getName());
        if (token == null) {
            token = (CsrfToken) request.getAttribute("_csrf");
        }
        if (token == null) {
            throw new IllegalStateException("CSRF token is unavailable");
        }
        return new CsrfTokenResponse(token.getToken(), token.getHeaderName());
    }
}
