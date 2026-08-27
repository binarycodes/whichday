package io.binarycodes.whichday.poll.domain;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;

import io.binarycodes.whichday.people.domain.Person;

/**
 * One person's answer. A voter who can make none of the days still has a ballot —
 * that is what separates "said no" from "has not answered", and it is the only
 * thing a counter-proposal can hang off.
 *
 * <p>No note. The screen used to collect one under the label "Note to the team" and no
 * screen ever showed it, so the label was the whole of the promise — see
 * {@code docs/REQUIREMENTS.md} §7. A day put forward says the same thing and is acted on.
 */
public record Ballot(Person voter, Set<LocalDate> chosenDays, List<LocalDate> proposedDays) {

    public boolean isDeclined() {
        return chosenDays.isEmpty();
    }
}
