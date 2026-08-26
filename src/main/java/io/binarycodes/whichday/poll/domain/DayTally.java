package io.binarycodes.whichday.poll.domain;

import java.time.LocalDate;
import java.util.List;

import io.binarycodes.whichday.people.domain.Person;

/**
 * One candidate day and everybody who said it works. The rank is what the bars
 * fade by, so it is settled here — alongside the ordering that produced it —
 * rather than counted again by each screen that draws them.
 *
 * @param rank    shared by days on the same count, so three days tied at the top are
 *                all rank 1 and their bars are painted alike. Two bars of identical
 *                length in different shades read as an order that is not there.
 * @param leading whether this day is <em>the</em> day in front — false for every day
 *                when the top count is shared, because then no day is. Handed in
 *                rather than derived from the rank: a tally cannot see its siblings,
 *                and "rank 1" and "won" stopped meaning the same thing.
 */
public record DayTally(LocalDate day, List<Person> voters, int rank, int inviteCount, boolean leading) {

    public int voteCount() {
        return voters.size();
    }

    public boolean isLeading() {
        return leading;
    }

    /** The bar's share of its track: votes out of everybody invited, never out of the leader. */
    public double share() {
        return inviteCount == 0 ? 0 : (double) voteCount() / inviteCount;
    }
}
