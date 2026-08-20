package io.binarycodes.findadate.poll.service;

import java.time.LocalDate;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import io.binarycodes.findadate.people.domain.Person;

/** One person's mutable answer. Package-private for the reason {@link StoredPoll} is. */
class StoredBallot {

    private final Person voter;
    private final Set<LocalDate> chosenDays;
    private final List<LocalDate> proposedDays;
    private final String note;

    StoredBallot(Person voter, Set<LocalDate> chosenDays, List<LocalDate> proposedDays, String note) {
        this.voter = voter;
        this.chosenDays = new LinkedHashSet<>(chosenDays);
        this.proposedDays = List.copyOf(proposedDays);
        this.note = note;
    }

    Person voter() {
        return voter;
    }

    Set<LocalDate> chosenDays() {
        return chosenDays;
    }

    List<LocalDate> proposedDays() {
        return proposedDays;
    }

    String note() {
        return note;
    }
}
