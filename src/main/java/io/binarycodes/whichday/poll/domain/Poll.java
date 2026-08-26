package io.binarycodes.whichday.poll.domain;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import io.binarycodes.whichday.people.domain.Person;

/**
 * A poll as a screen needs to read it: the candidate days, the standings, and the
 * answers that produced them. Everything derived is derived here, so that no view
 * counts votes.
 *
 * @param closesOn            the last day an answer counts. Voting is over from the
 *                            day after it — whole days, like everything else here.
 * @param alternativesAllowed whether a voter who can make none of the days may put
 *                            others forward. Saying no to the days is always an
 *                            answer; this is only about adding to the table.
 * @param state               settled by the service, which is what holds the clock
 */
public record Poll(UUID id,
                   String title,
                   Person organizer,
                   List<Person> invited,
                   List<LocalDate> candidateDays,
                   LocalDate closesOn,
                   LocalDate lockedDay,
                   Instant openedAt,
                   boolean alternativesAllowed,
                   PollState state,
                   List<DayTally> tallies,
                   List<Ballot> ballots) {

    public boolean isOpen() {
        return state == PollState.OPEN;
    }

    /** Voting is over and nobody locked a day in. Nothing more happens to it. */
    public boolean isClosed() {
        return state == PollState.CLOSED;
    }

    /**
     * Whether anything about this poll can still change. A draft is still being put
     * together and an open poll is still collecting; once voting is over the poll is
     * final, down to the invitee list and the locked day.
     */
    public boolean isEditable() {
        return state == PollState.DRAFT || state == PollState.OPEN;
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

    /**
     * The day in front, once anybody has voted for one at all — and only when it is
     * alone up there. A shared top is not a result, so this is empty and
     * {@link #tiedAtTheTop()} is what has something to say.
     */
    public Optional<DayTally> leader() {
        return tallies.stream().filter(DayTally::isLeading).findFirst();
    }

    /**
     * The days sharing the highest count, when more than one does. Empty when a single
     * day leads and empty when nobody has voted, so a caller that finds something here
     * has found a decision the group did not make and somebody has to.
     */
    public List<DayTally> tiedAtTheTop() {
        if (leader().isPresent() || isUnanswered()) {
            return List.of();
        }
        var top = tallies.stream().mapToInt(DayTally::voteCount).max().orElse(0);
        return top == 0 ? List.of() : tallies.stream().filter(tally -> tally.voteCount() == top).toList();
    }

    /**
     * Who still owes an answer apart from the person asking. The organizer is invited
     * like everybody else, so without this the results screen offers them a nudge to
     * themselves.
     */
    public List<Person> awaitingOthers(Person viewer) {
        return awaiting().stream().filter(person -> !person.equals(viewer)).toList();
    }

    public Optional<Ballot> ballotOf(Person voter) {
        return ballots.stream().filter(ballot -> ballot.voter().equals(voter)).findFirst();
    }
}
