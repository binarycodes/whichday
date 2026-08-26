package io.binarycodes.whichday.people.ui;

import java.util.List;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.html.Div;

import io.binarycodes.whichday.base.ui.Chip;
import io.binarycodes.whichday.people.domain.Person;

/**
 * People named rather than shown, for where an avatar cannot identify anybody.
 *
 * <p>Initials only work when the names behind them were settled in advance. Where a
 * visitor types their own name minutes before answering, one letter is as likely to be
 * a stranger's as a colleague's — so the name goes on the screen in full, and the tail
 * beyond {@code limit} becomes a count rather than pushing the row off the edge.
 *
 * <p>First names only: the rows that draw these are narrow, and a surname wraps them
 * onto another line without saying anything the first name did not.
 */
public final class NameChips {

    private NameChips() {
    }

    public static Div of(Component owner, List<Person> people, int limit) {
        var row = new Div();
        row.addClassName("name-chips");
        people.stream().limit(limit)
                .forEach(person -> row.add(new Chip(person.firstName(), Chip.Tone.OUTLINE)));
        var hidden = people.size() - limit;
        if (hidden > 0) {
            row.add(new Chip(owner.getTranslation("count.more", hidden), Chip.Tone.OUTLINE));
        }
        return row;
    }
}
