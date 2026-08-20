package io.binarycodes.findadate.people.ui;

import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Span;

import io.binarycodes.findadate.people.domain.Person;

/**
 * One line of the invite list: avatar, who they are, and where their invite has got
 * to. Somebody with no account has no name, so the address stands in for it.
 */
public class PersonRow extends Div {

    public PersonRow(Person person, String status) {
        addClassName("person-row");
        var name = new Span(person.displayName());
        name.addClassName("person-name");
        var state = new Span(status);
        state.addClassName("person-status");
        add(new PersonAvatar(person), name, state);
    }
}
