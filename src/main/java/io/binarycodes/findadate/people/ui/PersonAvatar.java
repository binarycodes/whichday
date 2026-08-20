package io.binarycodes.findadate.people.ui;

import com.vaadin.flow.component.avatar.Avatar;

import io.binarycodes.findadate.people.domain.Person;

/**
 * A person's initials in their own tone. The tone goes through Vaadin's colour
 * index rather than a class of ours, so the palette stays a theme decision.
 */
public class PersonAvatar extends Avatar {

    public PersonAvatar(Person person) {
        super(person.name());
        setAbbreviation(person.initials());
        setColorIndex(person.avatarTone());
    }
}
