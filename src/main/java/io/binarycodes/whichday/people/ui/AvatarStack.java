package io.binarycodes.whichday.people.ui;

import java.util.List;

import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Span;

import io.binarycodes.whichday.people.domain.Person;

/**
 * Overlapping avatars, capped so a large team does not push the row off the
 * screen: whoever does not fit becomes a "+3".
 */
public class AvatarStack extends Div {

    private static final int DEFAULT_LIMIT = 4;

    private final int limit;

    private boolean showOverflow = true;

    public AvatarStack() {
        this(DEFAULT_LIMIT);
    }

    public AvatarStack(int limit) {
        this.limit = limit;
        addClassName("avatar-stack");
    }

    /**
     * Show only the faces that fit and say nothing about the rest — for a field where
     * a count already follows the stack in words.
     */
    public AvatarStack hideOverflow() {
        this.showOverflow = false;
        return this;
    }

    /** Larger avatars, for the locked screen where the stack is the only content. */
    public AvatarStack large() {
        addClassName("avatar-stack-l");
        return this;
    }

    public AvatarStack show(List<Person> people) {
        removeAll();
        people.stream().limit(limit).map(PersonAvatar::new).forEach(this::add);
        if (showOverflow && people.size() > limit) {
            add(overflow("+" + (people.size() - limit)));
        }
        return this;
    }

    /**
     * The same stack, with whoever has not answered drawn as an outline after the
     * ones who have — the results screen's way of showing a gap rather than hiding it.
     */
    public AvatarStack show(List<Person> answered, List<Person> awaiting) {
        removeAll();
        answered.stream().limit(limit).map(PersonAvatar::new).forEach(this::add);
        var remaining = limit - Math.min(limit, answered.size());
        awaiting.stream().limit(Math.max(remaining, 1)).forEach(person -> {
            var pending = overflow(person.initials());
            pending.addClassName("avatar-stack-pending");
            add(pending);
        });
        return this;
    }

    private Span overflow(String label) {
        var span = new Span(label);
        span.addClassName("avatar-stack-overflow");
        return span;
    }
}
