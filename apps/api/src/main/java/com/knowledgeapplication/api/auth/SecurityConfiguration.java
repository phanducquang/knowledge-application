package com.knowledgeapplication.api.auth;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserRequest;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserService;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserService;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandlerImpl;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationFailureHandler;
import org.springframework.security.web.authentication.logout.HttpStatusReturningLogoutSuccessHandler;
import org.springframework.security.web.savedrequest.NullRequestCache;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.LinkedHashSet;
import java.util.Set;

@Configuration
public class SecurityConfiguration {

    public static final String OWNER_AUTHORITY = "ROLE_OWNER";

    @Bean
    SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            OAuth2UserService<OidcUserRequest, OidcUser> ownerOidcUserService,
            @Value("${app.auth.web-base-url}") String webBaseUrl
    ) throws Exception {
        AuthenticationEntryPoint apiEntryPoint = new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED);
        AccessDeniedHandlerImpl accessDeniedHandler = new AccessDeniedHandlerImpl();
        accessDeniedHandler.setErrorPage(null);

        http
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers("/actuator/health", "/oauth2/**", "/login/**", "/error").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/public/knowledge/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/shared/knowledge/**").permitAll()
                        .requestMatchers("/api/**").hasAuthority(OWNER_AUTHORITY)
                        .anyRequest().denyAll()
                )
                .exceptionHandling(exceptions -> exceptions
                        .defaultAuthenticationEntryPointFor(apiEntryPoint, apiRequestMatcher())
                        .accessDeniedHandler(accessDeniedHandler)
                )
                .requestCache(cache -> cache.requestCache(new NullRequestCache()))
                .csrf(csrf -> csrf.requireCsrfProtectionMatcher(authenticatedMutationMatcher()))
                .oauth2Login(oauth -> oauth
                        .userInfoEndpoint(userInfo -> userInfo.oidcUserService(ownerOidcUserService))
                        .defaultSuccessUrl(normalizeWebBaseUrl(webBaseUrl) + "/", true)
                        .failureHandler(new SimpleUrlAuthenticationFailureHandler(
                                normalizeWebBaseUrl(webBaseUrl) + "/login?error=unauthorized"
                        ))
                )
                .logout(logout -> logout
                        .logoutUrl("/api/auth/logout")
                        .invalidateHttpSession(true)
                        .clearAuthentication(true)
                        .deleteCookies("JSESSIONID")
                        .logoutSuccessHandler(new HttpStatusReturningLogoutSuccessHandler(HttpStatus.NO_CONTENT))
                );

        return http.build();
    }

    @Bean
    OAuth2UserService<OidcUserRequest, OidcUser> ownerOidcUserService(OwnerIdentity ownerIdentity) {
        OidcUserService delegate = new OidcUserService();
        return request -> {
            OidcUser principal = delegate.loadUser(request);
            if (!ownerIdentity.isAllowed(principal)) {
                throw new OAuth2AuthenticationException(
                        new OAuth2Error("unauthorized_owner"),
                        "Google account is not authorized for this workspace"
                );
            }

            Set<GrantedAuthority> authorities = new LinkedHashSet<>(principal.getAuthorities());
            authorities.add(new SimpleGrantedAuthority(OWNER_AUTHORITY));
            return new DefaultOidcUser(
                    authorities,
                    principal.getIdToken(),
                    principal.getUserInfo()
            );
        };
    }

    private static RequestMatcher authenticatedMutationMatcher() {
        return request -> {
            if (HttpMethod.GET.matches(request.getMethod())
                    || HttpMethod.HEAD.matches(request.getMethod())
                    || HttpMethod.OPTIONS.matches(request.getMethod())
                    || HttpMethod.TRACE.matches(request.getMethod())) {
                return false;
            }

            var authentication = SecurityContextHolder.getContext().getAuthentication();
            return authentication != null
                    && authentication.isAuthenticated()
                    && !(authentication instanceof AnonymousAuthenticationToken);
        };
    }

    private static RequestMatcher apiRequestMatcher() {
        return request -> request.getRequestURI().startsWith("/api/");
    }

    private static String normalizeWebBaseUrl(String webBaseUrl) {
        return webBaseUrl.endsWith("/")
                ? webBaseUrl.substring(0, webBaseUrl.length() - 1)
                : webBaseUrl;
    }
}
