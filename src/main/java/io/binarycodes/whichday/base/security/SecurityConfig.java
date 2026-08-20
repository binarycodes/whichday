package io.binarycodes.whichday.base.security;

import static com.vaadin.flow.spring.security.VaadinSecurityConfigurer.vaadin;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.ObjectPostProcessor;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.oidc.web.logout.OidcClientInitiatedLogoutSuccessHandler;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.header.HeaderWriterFilter;

/**
 * Signing in is the only way in, and OIDC is the only way to sign in.
 *
 * <p>There is no login view: {@code oauth2LoginPage} points at the registration, so
 * an unauthenticated request redirects to the provider rather than to a form of ours.
 * The application collects no credentials and never sees one.
 */
@Configuration
public class SecurityConfig {

    private static final String REGISTRATION_ID = "oidc";

    /**
     * The stylesheet's own partials. Vaadin permits the resources it knows about, which
     * includes the {@code @StyleSheet} entry point but not the files that entry point
     * {@code @import}s — those are plain requests it has never heard of. Left
     * authenticated they redirect to the provider, and a 302 carries no content type,
     * so the browser refuses every one of them as "not a supported stylesheet MIME
     * type" and the application renders unstyled.
     */
    private static final String STYLE_PARTIALS = "/styles/**";
    private static final String AUTHORIZATION_ENDPOINT = "/oauth2/authorization/" + REGISTRATION_ID;

    private static final String MISCONFIGURED = """
            Signing in is the only way into Whichday, and it is not configured: %s.

            Set WHICHDAY_OIDC_CLIENT_ID and WHICHDAY_OIDC_CLIENT_SECRET from an OAuth
            client whose redirect URI is <base-url>/login/oauth2/code/oidc, and
            WHICHDAY_OIDC_ISSUER_URI if the provider is not the default.""";

    /**
     * A logout that only drops our own session leaves the provider's intact, and the
     * next visit signs straight back in. This is what makes the button mean what it
     * says.
     */
    @Bean
    SecurityFilterChain filterChain(HttpSecurity http, ClientRegistrationRepository registrations)
            throws Exception {
        requireCredentials(registrations);
        var logout = new OidcClientInitiatedLogoutSuccessHandler(registrations);
        logout.setPostLogoutRedirectUri("{baseUrl}");
        return http
                .authorizeHttpRequests(requests -> requests.requestMatchers(STYLE_PARTIALS).permitAll())
                .with(vaadin(), configurer -> configurer
                        .oauth2LoginPage(AUTHORIZATION_ENDPOINT)
                        .logoutSuccessHandler(logout))
                .build();
    }

    /**
     * Refuses to start without credentials.
     *
     * <p>Spring does not do this for us: an unresolved {@code ${...}} placeholder in a
     * properties file binds as the literal string, so the application starts, fetches
     * the provider's discovery document, and redirects to a real authorization endpoint
     * carrying a client id of "${WHICHDAY_OIDC_CLIENT_ID}". The first person to try
     * signing in meets the provider's error page. Signing in is the only way into this
     * application, so a missing client is a startup failure and not a surprise later.
     */
    private static void requireCredentials(ClientRegistrationRepository registrations) {
        var registration = registrations.findByRegistrationId(REGISTRATION_ID);
        if (registration == null) {
            throw new IllegalStateException(MISCONFIGURED.formatted("no registration named "
                    + REGISTRATION_ID));
        }
        if (isUnset(registration.getClientId()) || isUnset(registration.getClientSecret())) {
            throw new IllegalStateException(MISCONFIGURED.formatted("no client id or secret"));
        }
    }

    /** Blank, absent, or an environment variable nobody set. */
    private static boolean isUnset(String value) {
        return value == null || value.isBlank() || value.contains("${");
    }

    /**
     * Spring Security writes its headers as the response commits, and the response
     * Vaadin renders a page into never commits that way — so the application's own
     * routes would come out with no headers at all while static resources got the
     * full set. Verify a header change by curling a route, never only a static file
     * (CODING_CONVENTIONS.md §10a).
     */
    @Bean
    ObjectPostProcessor<HeaderWriterFilter> headersWrittenEagerly() {
        return new ObjectPostProcessor<>() {
            @Override
            public <O extends HeaderWriterFilter> O postProcess(O filter) {
                filter.setShouldWriteHeadersEagerly(true);
                return filter;
            }
        };
    }
}
