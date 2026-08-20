package io.binarycodes.whichday;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;

import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.StandardClaimNames;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;

import io.binarycodes.whichday.people.domain.Person;

/**
 * Signs somebody in without a provider.
 *
 * <p>It puts a real {@code OAuth2AuthenticationToken} into the security context
 * rather than replacing {@code ViewerSession}, for two reasons. A browserless test
 * bypasses the servlet filter chain but not {@code SpringNavigationAccessControl}, so
 * every {@code @PermitAll} route needs an authenticated principal or navigation lands
 * nowhere. And going through the token means the application's own
 * {@code AuthenticatedViewerSession} is what the tests exercise, claims and all.
 */
public final class StubIdentity {

    private static final String REGISTRATION_ID = "oidc";

    private StubIdentity() {
    }

    public static void signIn(Person person) {
        var claims = Map.<String, Object>of(
                StandardClaimNames.SUB, person.email(),
                StandardClaimNames.EMAIL, person.email(),
                StandardClaimNames.NAME, person.name());
        var idToken = new OidcIdToken("stub-token", Instant.now(),
                Instant.now().plus(1, ChronoUnit.HOURS), claims);
        var user = new DefaultOidcUser(List.of(new SimpleGrantedAuthority("ROLE_USER")),
                idToken, StandardClaimNames.EMAIL);
        SecurityContextHolder.getContext().setAuthentication(
                new OAuth2AuthenticationToken(user, user.getAuthorities(), REGISTRATION_ID));
    }

    public static void clear() {
        SecurityContextHolder.clearContext();
    }
}
