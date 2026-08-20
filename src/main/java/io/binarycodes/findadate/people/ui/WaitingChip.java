package io.binarycodes.findadate.people.ui;

import com.vaadin.flow.component.html.Span;

import io.binarycodes.findadate.people.domain.Person;

/** Whoever still owes an answer, as a chip with their face on it. */
public class WaitingChip extends Span {

    public WaitingChip(Person person) {
        addClassNames("chip", "chip-outline", "waiting-chip");
        add(new PersonAvatar(person), new Span(person.firstName()));
    }
}
