package io.binarycodes.findadate.people.ui.presenter;

import java.util.List;

import org.springframework.stereotype.Component;

import com.vaadin.flow.spring.annotation.VaadinSessionScope;

import io.binarycodes.findadate.people.domain.Person;
import io.binarycodes.findadate.people.service.TeamDirectory;

/**
 * Who this browser is. Session-scoped because it is a property of the session and
 * of nothing else — the store below it is shared by everybody who has the link.
 *
 * <p>It stands in for the authenticated subject a signed-in deployment would read,
 * and it is switchable, which is what makes both the organizer's and a voter's half
 * of the design reachable without a login.
 */
@Component
@VaadinSessionScope
public class ViewerSession {

    private final TeamDirectory directory;

    private Person viewer;

    public ViewerSession(TeamDirectory directory) {
        this.directory = directory;
        this.viewer = directory.defaultViewer();
    }

    public Person viewer() {
        return viewer;
    }

    public void switchTo(Person person) {
        this.viewer = person;
    }

    public List<Person> everyone() {
        return directory.members();
    }

    public String teamName() {
        return directory.teamName();
    }
}
