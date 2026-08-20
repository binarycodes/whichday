package io.binarycodes.findadate.people.ui;

import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Span;

import io.binarycodes.findadate.people.domain.Person;

/** One line of the invite list: avatar, name, and where their invite has got to. */
public class PersonRow extends Div {

    public PersonRow(Person person, String status) {
        addClassName("person-row");
        var name = new Span(person.name());
        name.addClassName("person-name");
        var state = new Span(status);
        state.addClassName("person-status");
        add(new PersonAvatar(person), name, state);
    }
}
