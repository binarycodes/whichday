package io.binarycodes.whichday.poll.domain;

import java.time.LocalDate;
import java.util.List;

import io.binarycodes.whichday.people.domain.Person;

/**
 * One candidate day and everybody who said it works. The rank is what the bars
 * fade by, so it is settled here — alongside the ordering that produced it —
 * rather than counted again by each screen that draws them.
 */
public record DayTally(LocalDate day, List<Person> voters, int rank, int inviteCount) {

    public int voteCount() {
        return voters.size();
    }

    public boolean isLeading() {
        return rank == 1 && !voters.isEmpty();
    }

    /** The bar's share of its track: votes out of everybody invited, never out of the leader. */
    public double share() {
        return inviteCount == 0 ? 0 : (double) voteCount() / inviteCount;
    }
}
