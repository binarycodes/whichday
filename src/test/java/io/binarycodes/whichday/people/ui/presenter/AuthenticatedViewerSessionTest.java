package io.binarycodes.whichday.people.ui.presenter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.StandardClaimNames;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;

import io.binarycodes.whichday.TestDatabase;
import io.binarycodes.whichday.WhichdayTest;
import io.binarycodes.whichday.people.service.AccountDirectory;

@WhichdayTest
@DisplayName("Who is signed in")
class AuthenticatedViewerSessionTest {

    @Autowired
    private AccountDirectory directory;

    @Autowired
    private AuthenticatedViewerSession session;

    @Autowired
    private TestDatabase database;

    @BeforeEach
    void setUp() {
        database.empty();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("is whoever the token says, and becomes an account on the way past")
    void readsTheToken() {
        signIn(Map.of(StandardClaimNames.SUB, "abc",
                StandardClaimNames.EMAIL, "Sara.Naslund@ACME.com",
                StandardClaimNames.NAME, "Sara Näslund"));

        var viewer = session.viewer();

        assertThat(viewer.email()).isEqualTo("sara.naslund@acme.com");
        assertThat(viewer.name()).isEqualTo("Sara Näslund");
        assertThat(directory.byEmail("sara.naslund@acme.com")).contains(viewer);
    }

    @Test
    @DisplayName("falls back to the subject when the provider withholds an address")
    void fallsBackToTheSubject() {
        signIn(Map.of(StandardClaimNames.SUB, "subject-only",
                StandardClaimNames.NAME, "No Address"));

        var viewer = session.viewer();

        assertThat(viewer.email()).isEqualTo("subject-only");
        assertThat(viewer.name()).isEqualTo("No Address");
    }

    @Test
    @DisplayName("works for an account that gave no name at all")
    void anAccountWithoutAName() {
        signIn(Map.of(StandardClaimNames.SUB, "abc", StandardClaimNames.EMAIL, "nameless@acme.com"));

        var viewer = session.viewer();

        assertThat(viewer.hasAccount()).isFalse();
        assertThat(viewer.displayName()).isEqualTo("nameless@acme.com");
    }

    @Test
    @DisplayName("refuses rather than defaulting when nobody is signed in")
    void refusesWhenAnonymous() {
        assertThatThrownBy(() -> session.viewer())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("No authenticated user");
        // Nothing was written either, which is the half a stub directory could not have shown.
        assertThat(directory.byEmail("anybody@acme.com")).isEmpty();
    }

    private void signIn(Map<String, Object> claims) {
        var idToken = new OidcIdToken("token", Instant.now(),
                Instant.now().plus(1, ChronoUnit.HOURS), claims);
        var user = new DefaultOidcUser(List.of(new SimpleGrantedAuthority("ROLE_USER")),
                idToken, StandardClaimNames.SUB);
        SecurityContextHolder.getContext().setAuthentication(
                new OAuth2AuthenticationToken(user, user.getAuthorities(), "oidc"));
    }
}
