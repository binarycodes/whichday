package io.binarycodes.whichday.poll.domain;

import java.time.LocalDate;

import io.binarycodes.whichday.people.domain.Person;

/**
 * A poll as it appears in a list: the one date worth showing, and just enough
 * context to know whether it is waiting on you.
 *
 * @param headlineDay        the locked day, or the day in front, or null while the
 *                           team has not landed on anything
 * @param firstCandidateDay  the earliest day on the table, so that a poll with no
 *                           leader is still anchored in a month
 */
public record PollSummary(String slug,
                          String title,
                          Person askedBy,
                          LocalDate headlineDay,
                          LocalDate firstCandidateDay,
                          LocalDate closesOn,
                          int candidateDayCount,
                          int voteCount,
                          int inviteCount,
                          boolean answeredByViewer,
                          PollState state) {

    public boolean isSettled() {
        return state == PollState.LOCKED;
    }

    /** Named but never sent. Nobody else can see it, so nobody else is waiting on it. */
    public boolean isDraft() {
        return state == PollState.DRAFT;
    }

    /** Voting is over; whether it produced a date is the organizer's to confirm. */
    public boolean isClosed() {
        return state == PollState.CLOSED;
    }

    /** Still worth the viewer's attention: open, and unanswered by them. */
    public boolean needsViewer() {
        return state == PollState.OPEN && !answeredByViewer;
    }

    public boolean hasHeadlineDay() {
        return headlineDay != null;
    }
}
