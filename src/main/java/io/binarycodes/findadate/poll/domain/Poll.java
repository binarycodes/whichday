package io.binarycodes.findadate.poll.domain;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import io.binarycodes.findadate.people.domain.Person;

/**
 * A poll as a screen needs to read it: the candidate days, the standings, and the
 * answers that produced them. Everything derived is derived here, so that no view
 * counts votes.
 */
public record Poll(String slug,
                   String title,
                   Person organizer,
                   List<Person> invited,
                   List<LocalDate> candidateDays,
                   LocalDateTime closesAt,
                   LocalDate lockedDay,
                   Instant openedAt,
                   List<DayTally> tallies,
                   List<Ballot> ballots) {

    public PollState state() {
        if (lockedDay != null) {
            return PollState.LOCKED;
        }
        return candidateDays.isEmpty() || closesAt == null ? PollState.DRAFT : PollState.OPEN;
    }

    public int inviteCount() {
        return invited.size();
    }

    /** Everybody who has answered — including anybody who answered that none of the days work. */
    public int answerCount() {
        return ballots.size();
    }

    public boolean isUnanswered() {
        return ballots.isEmpty();
    }

    public List<Person> answered() {
        return ballots.stream().map(Ballot::voter).toList();
    }

    public List<Person> awaiting() {
        var answered = answered();
        return invited.stream().filter(person -> !answered.contains(person)).toList();
    }

    public List<Ballot> declined() {
        return ballots.stream().filter(Ballot::isDeclined).toList();
    }

    /** The day in front, once anybody has voted for one at all. */
    public Optional<DayTally> leader() {
        return tallies.stream().filter(DayTally::isLeading).findFirst();
    }

    public Optional<Person> soleHoldout() {
        var awaiting = awaiting();
        return awaiting.size() == 1 ? Optional.of(awaiting.getFirst()) : Optional.empty();
    }

    public Optional<Ballot> ballotOf(Person voter) {
        return ballots.stream().filter(ballot -> ballot.voter().equals(voter)).findFirst();
    }
}
