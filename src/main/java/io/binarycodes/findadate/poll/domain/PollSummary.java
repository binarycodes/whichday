package io.binarycodes.findadate.poll.domain;

import java.time.LocalDate;
import java.time.LocalDateTime;

import io.binarycodes.findadate.people.domain.Person;

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
                          LocalDateTime closesAt,
                          int candidateDayCount,
                          int voteCount,
                          int inviteCount,
                          boolean answeredByViewer,
                          PollState state) {

    public boolean isSettled() {
        return state == PollState.LOCKED;
    }

    public boolean hasHeadlineDay() {
        return headlineDay != null;
    }
}
