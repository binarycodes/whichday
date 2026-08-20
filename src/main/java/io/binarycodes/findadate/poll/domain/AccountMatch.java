package io.binarycodes.findadate.poll.domain;

import io.binarycodes.findadate.people.domain.Person;

/**
 * A person the organizer's query found, and why they are somebody the organizer
 * plausibly meant: polls the two have answered together, or nothing more than a
 * shared workspace.
 *
 * <p>The reason is a count rather than a sentence so the wording stays in the view,
 * where the translations are.
 */
public record AccountMatch(Person person, int sharedPolls) {

    public boolean hasHistory() {
        return sharedPolls > 0;
    }
}
