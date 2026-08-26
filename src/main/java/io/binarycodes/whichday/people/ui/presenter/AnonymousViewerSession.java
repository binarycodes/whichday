package io.binarycodes.whichday.people.ui.presenter;

import java.time.Clock;
import java.util.Optional;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.vaadin.flow.component.UI;
import com.vaadin.flow.server.VaadinSession;
import com.vaadin.flow.spring.annotation.VaadinSessionScope;

import io.binarycodes.whichday.people.domain.AnonymousAddress;
import io.binarycodes.whichday.people.domain.Person;
import io.binarycodes.whichday.people.service.AccountDirectory;

/**
 * Somebody who typed a name. That is the whole of identity in anonymous mode: there
 * is no provider to vouch for anybody and no account to look anybody up in.
 *
 * <p>The name is written to the {@code account} table, which is not a claim that
 * anybody authenticated — it is the one place a name lives, and a poll stores nothing
 * but addresses. Skip the write and every screen reads the minted address back out
 * wherever a name belongs, including on other people's ballots. What the table holds
 * in this mode is a session's chosen name, and nothing consults it beyond rendering:
 * the invitee search is the only reader and it is not part of anonymous mode.
 */
@Component
@VaadinSessionScope
@ConditionalOnProperty(name = "whichday.access.mode", havingValue = "anonymous")
public class AnonymousViewerSession implements ViewerSession {

    private final Clock clock;
    private final AccountDirectory directory;

    private Person viewer;
    private String adminCode = "";

    public AnonymousViewerSession(Clock clock, AccountDirectory directory) {
        this.clock = clock;
        this.directory = directory;
    }

    @Override
    public Person viewer() {
        if (viewer == null) {
            throw new IllegalStateException("Nobody has said who they are");
        }
        return viewer;
    }

    /**
     * Closes the session outright, because there is no provider to log out of and
     * nothing else holding the identity — the minted address only ever existed here.
     * The reload is what puts the browser back in front of the who-are-you screen.
     */
    @Override
    public void signOut() {
        var page = UI.getCurrent().getPage();
        VaadinSession.getCurrent().getSession().invalidate();
        VaadinSession.getCurrent().close();
        page.setLocation("/");
    }

    @Override
    public boolean isIdentified() {
        return viewer != null;
    }

    @Override
    public Optional<String> adminCode() {
        return adminCode.isBlank() ? Optional.empty() : Optional.of(adminCode);
    }

    /**
     * The address is minted once and then kept, because it is what every poll, ballot
     * and invitee row this session writes will be keyed on — a second one would make
     * the same person a stranger to their own answers.
     */
    @Override
    public void identify(String name, String adminCode) {
        this.adminCode = adminCode == null ? "" : adminCode.trim();
        viewer = Person.signedIn(
                viewer == null ? AnonymousAddress.mintedAt(clock) : viewer.email(), name);
        directory.remember(viewer);
    }
}
