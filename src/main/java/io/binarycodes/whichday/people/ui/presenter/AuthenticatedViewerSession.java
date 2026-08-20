package io.binarycodes.whichday.people.ui.presenter;

import org.springframework.security.oauth2.core.oidc.StandardClaimNames;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.stereotype.Component;

import com.vaadin.flow.spring.security.AuthenticationContext;

import io.binarycodes.whichday.people.domain.Person;
import io.binarycodes.whichday.people.service.AccountDirectory;

/**
 * The signed-in user, read from the OIDC token. There is no way to become somebody
 * else: the provider decides, and every screen asks here.
 */
@Component
public class AuthenticatedViewerSession implements ViewerSession {

    private final AuthenticationContext authentication;
    private final AccountDirectory directory;

    public AuthenticatedViewerSession(AuthenticationContext authentication, AccountDirectory directory) {
        this.authentication = authentication;
        this.directory = directory;
    }

    /**
     * Remembered as an account on the way past, so that a colleague can find them by
     * address later. That is the only way the directory ever learns about anybody.
     */
    @Override
    public Person viewer() {
        var person = authentication.getAuthenticatedUser(OidcUser.class)
                .map(AuthenticatedViewerSession::personFor)
                .orElseThrow(() -> new IllegalStateException("No authenticated user"));
        directory.remember(person);
        return person;
    }

    @Override
    public void signOut() {
        authentication.logout();
    }

    /**
     * An address is how everybody is identified here, so an account without one falls
     * back to the subject — which is stable even when the provider withholds an email.
     */
    private static Person personFor(OidcUser user) {
        var email = user.getEmail() == null
                ? user.getClaimAsString(StandardClaimNames.SUB)
                : user.getEmail();
        return Person.signedIn(email, user.getFullName());
    }
}
