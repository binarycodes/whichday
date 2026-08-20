package io.binarycodes.whichday.people.ui;

import java.util.List;
import java.util.function.Consumer;

import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.NativeButton;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.icon.VaadinIcon;

import io.binarycodes.whichday.people.domain.Person;

/**
 * Who is on a poll so far, as removable chips that wrap — the shape the design's
 * create screen and invitee screen share.
 *
 * <p>The trailing element is a prompt rather than a text input: on the create screen
 * it opens the screen that does the searching, and on that screen the real field is
 * above. One control per job keeps a phone keyboard from opening over the chips.
 */
public class InviteeChips extends Div {

    public InviteeChips(List<Person> invitees,
                        String removeLabel,
                        String prompt,
                        Consumer<Person> onRemove,
                        Runnable onPrompt) {
        addClassName("invitee-chips");
        invitees.forEach(person -> add(chipFor(person, removeLabel, onRemove)));

        var add = new NativeButton(prompt);
        add.addClassName("invitee-prompt");
        add.addClickListener(ignored -> onPrompt.run());
        add(add);
    }

    private Span chipFor(Person person, String removeLabel, Consumer<Person> onRemove) {
        var chip = new Span();
        chip.addClassNames("chip", "chip-accent", "invitee-chip");

        var address = new Span(person.email());
        address.addClassName("invitee-email");
        address.getElement().setAttribute("title", person.email());

        var remove = new NativeButton();
        remove.add(new Icon(VaadinIcon.CLOSE_SMALL));
        remove.addClassName("invitee-remove");
        remove.setAriaLabel(removeLabel.formatted(person.email()));
        remove.addClickListener(ignored -> onRemove.accept(person));

        chip.add(new PersonAvatar(person), address, remove);
        return chip;
    }
}
