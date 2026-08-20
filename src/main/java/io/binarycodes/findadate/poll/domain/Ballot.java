package io.binarycodes.findadate.poll.domain;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;

import io.binarycodes.findadate.people.domain.Person;

/**
 * One person's answer. A voter who can make none of the days still has a ballot —
 * that is what separates "said no" from "has not answered", and it is the only
 * thing a counter-proposal can hang off.
 */
public record Ballot(Person voter, Set<LocalDate> chosenDays, List<LocalDate> proposedDays, String note) {

    public boolean isDeclined() {
        return chosenDays.isEmpty();
    }
}
