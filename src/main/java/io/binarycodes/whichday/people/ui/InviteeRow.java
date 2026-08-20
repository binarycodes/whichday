package io.binarycodes.whichday.people.ui;

import java.util.function.Consumer;

import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.NativeButton;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.icon.VaadinIcon;

import io.binarycodes.whichday.people.domain.Person;

/**
 * Somebody already on the poll, with the way to take them off again. Shows the
 * address rather than the name: this is the list you check against what you typed,
 * and two colleagues can share a first name where they cannot share a mailbox.
 */
public class InviteeRow extends Div {

    public InviteeRow(Person person, String removeLabel, Consumer<Person> onRemove) {
        addClassName("person-row");

        var address = new Span(person.email());
        address.addClassName("person-name");

        var remove = new NativeButton();
        remove.add(new Icon(VaadinIcon.CLOSE_SMALL));
        remove.addClassName("invitee-remove");
        remove.setAriaLabel(removeLabel.formatted(person.email()));
        remove.addClickListener(ignored -> onRemove.accept(person));

        add(new PersonAvatar(person), address, remove);
    }
}
