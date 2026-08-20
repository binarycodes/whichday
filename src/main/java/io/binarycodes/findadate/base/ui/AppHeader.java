package io.binarycodes.findadate.base.ui;

import java.util.List;
import java.util.function.Consumer;

import com.vaadin.flow.component.avatar.Avatar;
import com.vaadin.flow.component.contextmenu.MenuItem;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.menubar.MenuBar;

import io.binarycodes.findadate.people.domain.Person;

/**
 * The wordmark and the viewer's avatar, which is where the design puts the account.
 *
 * <p>With no login yet, that avatar is also how you become somebody else — the
 * organizer's screens and a voter's screens are the same application, and without
 * a switcher only one of the two would be reachable. See
 * {@code docs/clarifications/0003-voter-identity.md}.
 */
public class AppHeader extends Div {

    public AppHeader(String wordmark, Person viewer, List<Person> people, Consumer<Person> onSwitch) {
        addClassName("app-header");

        var name = new Span(wordmark);
        name.addClassName("wordmark");

        var switcher = new MenuBar();
        switcher.addThemeName("tertiary-inline");
        var trigger = switcher.addItem(avatarOf(viewer));
        people.forEach(person -> addChoice(trigger, person, viewer, onSwitch));

        add(name, switcher);
    }

    private void addChoice(MenuItem trigger, Person person, Person viewer, Consumer<Person> onSwitch) {
        var choice = trigger.getSubMenu().addItem(person.name(), ignored -> onSwitch.accept(person));
        choice.setEnabled(!person.equals(viewer));
    }

    private static Avatar avatarOf(Person person) {
        var avatar = new Avatar(person.name());
        avatar.setAbbreviation(person.initials());
        avatar.setColorIndex(person.avatarTone());
        return avatar;
    }
}
