package io.binarycodes.whichday.people.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import io.binarycodes.whichday.AnonymousWhichdayTest;
import io.binarycodes.whichday.Sample;
import io.binarycodes.whichday.TestClock;
import io.binarycodes.whichday.TestDatabase;
import io.binarycodes.whichday.people.domain.AnonymousAddress;
import io.binarycodes.whichday.people.domain.Person;
import io.binarycodes.whichday.poll.domain.Caller;
import io.binarycodes.whichday.poll.service.PollService;

/**
 * What becomes of a name somebody typed into the who-are-you screen. Anonymous mode,
 * because that is the only mode that mints an address — though the rule itself asks about
 * the address and not about the mode.
 *
 * <p>The sweep is two calls in the order {@code RetentionSweep} makes them: the polls
 * first, then the names nothing refers to any more. Called directly here, since the
 * schedule is off under the test profile.
 */
@AnonymousWhichdayTest
@DisplayName("Forgetting a name nothing refers to")
class AccountRetentionTest {

    /** The default {@code WHICHDAY_RETENTION_DAYS}, which governs accounts as well as polls. */
    private static final int MAXIMUM_AGE = 90;

    @Autowired
    private TestClock clock;

    @Autowired
    private TestDatabase database;

    @Autowired
    private AccountDirectory directory;

    @Autowired
    private PollService polls;

    @BeforeEach
    void setUp() {
        database.empty();
        clock.reset();
    }

    @Test
    @DisplayName("keeps a name for its whole window and drops it the day after")
    void dropsAnAbandonedName() {
        var visitor = typedAName("Ada");

        clock.advanceDays(MAXIMUM_AGE);
        assertThat(sweep()).isZero();
        assertThat(directory.byEmail(visitor.email())).isPresent();

        clock.advanceDays(1);
        assertThat(sweep()).isEqualTo(1);
        assertThat(directory.byEmail(visitor.email())).isEmpty();
    }

    /**
     * The whole of the condition: a name a poll still mentions is a name that poll still
     * has to show, however old the row is. Dropping it would render that person as their
     * own minted address on everybody else's screen.
     */
    @Test
    @DisplayName("keeps a name past its window while a poll still refers to it")
    void keepsANameAPollStillNeeds() {
        var invitee = typedAName("Sara");

        clock.advanceDays(80);
        var organizer = typedAName("Miro");
        pollBy(organizer, List.of(organizer, invitee));

        clock.advanceDays(11);
        assertThat(sweep()).isZero();
        assertThat(directory.byEmail(invitee.email())).isPresent();

        // Once that poll reaches its own maximum age, nothing refers to either of them.
        clock.advanceDays(MAXIMUM_AGE);
        assertThat(sweep()).isEqualTo(2);
        assertThat(directory.byEmail(invitee.email())).isEmpty();
        assertThat(directory.byEmail(organizer.email())).isEmpty();
    }

    /**
     * An address a provider vouched for belongs to somebody who can come back and be
     * recognised. A minted one belonged to a session that no longer exists, and that is
     * the whole difference.
     */
    @Test
    @DisplayName("never drops an address a provider vouched for, at any age")
    void keepsWhatAProviderVouchedFor() {
        directory.remember(Sample.ADA);

        clock.advanceDays(MAXIMUM_AGE * 4);

        assertThat(sweep()).isZero();
        assertThat(directory.byEmail(Sample.ADA.email())).isPresent();
    }

    /** A voter's name outlives their poll by one sweep, and no longer. */
    @Test
    @DisplayName("drops a voter's name once the poll they answered has gone")
    void aVotersNameGoesWithTheirPoll() {
        var organizer = typedAName("Ada");
        var voter = typedAName("Tom");
        var poll = pollBy(organizer, List.of(organizer));
        polls.castVote(poll, voter, Set.of(polls.poll(poll, organizer).orElseThrow()
                .candidateDays().getFirst()));

        clock.advanceDays(MAXIMUM_AGE + 1);

        assertThat(polls.deleteExpiredPolls()).isEqualTo(1);
        assertThat(directory.forgetExpiredAnonymous(polls.addressesOnAnyPoll())).isEqualTo(2);
        assertThat(directory.byEmail(voter.email())).isEmpty();
        assertThat(database.rowsIn("account")).isZero();
    }

    /** What the who-are-you screen does: mint an address, then remember the name on it. */
    private Person typedAName(String name) {
        var visitor = Person.signedIn(AnonymousAddress.mintedAt(clock), name);
        directory.remember(visitor);
        return visitor;
    }

    private UUID pollBy(Person organizer, List<Person> invited) {
        var id = polls.create("Q3 offsite", organizer, invited);
        polls.replaceCandidateDays(id, Caller.of(organizer),
                List.of(Sample.mondayAfterNext(LocalDate.now(clock))));
        polls.send(id, Caller.of(organizer));
        return id;
    }

    private int sweep() {
        polls.deleteExpiredPolls();
        return directory.forgetExpiredAnonymous(polls.addressesOnAnyPoll());
    }
}
