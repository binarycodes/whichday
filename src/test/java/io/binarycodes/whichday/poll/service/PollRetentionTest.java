package io.binarycodes.whichday.poll.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import io.binarycodes.whichday.Sample;
import io.binarycodes.whichday.TestClock;
import io.binarycodes.whichday.TestDatabase;
import io.binarycodes.whichday.WhichdayTest;
import io.binarycodes.whichday.people.service.AccountDirectory;
import io.binarycodes.whichday.poll.domain.Caller;
import io.binarycodes.whichday.poll.domain.PollState;

/**
 * The retention sweep, at the two windows a deployment gets by default: five days after
 * a poll ends, and ninety from the day it was made whatever state it is in.
 *
 * <p>Every case moves {@link TestClock} rather than computing a cutoff, so what is being
 * checked is the rule a deployment lives with and not an arithmetic restatement of it.
 */
@WhichdayTest
@DisplayName("Sweeping polls that are past their window")
class PollRetentionTest {

    /** The defaults in {@code application.properties}, which the test profile leaves alone. */
    private static final int AFTER_POLL_ENDS = 5;
    private static final int MAXIMUM_AGE = 90;

    @Autowired
    private TestClock clock;

    @Autowired
    private TestDatabase database;

    @Autowired
    private PollService service;

    @Autowired
    private AccountDirectory directory;

    @BeforeEach
    void setUp() {
        database.empty();
        clock.reset();
        Sample.signedInBefore(directory);
    }

    @Test
    @DisplayName("keeps a poll for its whole window and deletes it the day after")
    void endedPollGoesWhenTheWindowHasPassed() {
        var ends = today().plusDays(7);
        var id = pollClosingOn(ends);
        clock.advanceDays(8);
        assertThat(state(id)).isEqualTo(PollState.CLOSED);

        clock.advanceDays(4);
        assertThat(service.deleteExpiredPolls()).isZero();
        assertThat(service.poll(id, Sample.ADA)).isPresent();
        assertThat(today()).isEqualTo(ends.plusDays(AFTER_POLL_ENDS));

        clock.advanceDays(1);
        assertThat(service.deleteExpiredPolls()).isEqualTo(1);
        assertThat(service.poll(id, Sample.ADA)).isEmpty();
    }

    /**
     * The case the anchor exists for: a poll settled for a day after it stopped taking
     * answers has not happened yet, and its closing date is five days gone while the
     * team is still waiting on the date it went there to find.
     */
    @Test
    @DisplayName("measures a settled poll from the day locked in, not the day it closed")
    void settledPollGoesFromItsLockedDay() {
        var soon = today().plusDays(3);
        var meeting = today().plusDays(30);
        var id = service.create("Sprint retro", Sample.ADA, Sample.TEAM);
        service.replaceCandidateDays(id, Caller.of(Sample.ADA), List.of(soon, meeting));
        service.send(id, Caller.of(Sample.ADA));
        service.closeOn(id, Caller.of(Sample.ADA), soon);
        service.lock(id, Caller.of(Sample.ADA), meeting);

        clock.advanceDays(9);
        assertThat(service.deleteExpiredPolls()).isZero();
        assertThat(service.poll(id, Sample.ADA)).isPresent();

        clock.advanceDays(27);
        assertThat(today()).isEqualTo(meeting.plusDays(AFTER_POLL_ENDS + 1));
        assertThat(service.deleteExpiredPolls()).isEqualTo(1);
        assertThat(service.poll(id, Sample.ADA)).isEmpty();
    }

    @Test
    @DisplayName("leaves a draft and a poll still taking answers alone")
    void nothingUnfinishedGoesEarly() {
        var draft = service.create("Not sent yet", Sample.ADA, Sample.TEAM);
        var open = pollClosingOn(today().plusDays(200));

        clock.advanceDays(40);

        assertThat(service.deleteExpiredPolls()).isZero();
        assertThat(service.poll(draft, Sample.ADA)).isPresent();
        assertThat(state(open)).isEqualTo(PollState.OPEN);
    }

    /**
     * The ceiling, and the whole reason there is one: a draft has no date to have ended
     * on and an open poll's closing date can be months out, so without a rule measured
     * from creation there are rows no rule reaches.
     */
    @Test
    @DisplayName("takes everything at the maximum age, answers still coming in or not")
    void maximumAgeTakesEverything() {
        var draft = service.create("Never sent", Sample.ADA, Sample.TEAM);
        var open = pollClosingOn(today().plusDays(200));
        var ended = pollClosingOn(today().plusDays(2));
        var settled = Sample.settled(service, clock);

        clock.advanceDays(MAXIMUM_AGE + 1);

        assertThat(state(open)).isEqualTo(PollState.OPEN);
        assertThat(service.deleteExpiredPolls()).isEqualTo(4);
        assertThat(database.rowsIn("poll")).isZero();
        assertThat(List.of(draft, open, ended, settled))
                .allSatisfy(id -> assertThat(service.poll(id, Sample.ADA)).isEmpty());
    }

    @Test
    @DisplayName("keeps a draft for its ninetieth day and deletes it on the next")
    void maximumAgeBoundary() {
        var draft = service.create("Never sent", Sample.ADA, Sample.TEAM);

        clock.advanceDays(MAXIMUM_AGE);
        assertThat(service.deleteExpiredPolls()).isZero();
        assertThat(service.poll(draft, Sample.ADA)).isPresent();

        clock.advanceDays(1);
        assertThat(service.deleteExpiredPolls()).isEqualTo(1);
        assertThat(service.poll(draft, Sample.ADA)).isEmpty();
    }

    /** Both windows reach the same poll here, and it is still one poll. */
    @Test
    @DisplayName("deletes a poll both windows have passed once")
    void countedOnce() {
        pollClosingOn(today().plusDays(2));

        clock.advanceDays(MAXIMUM_AGE + 1);

        assertThat(service.deleteExpiredPolls()).isEqualTo(1);
    }

    /**
     * Everything about the poll, which is what makes a saved link read as a link to a
     * poll that never existed. The account rows are not this sweep's to touch: dropping a
     * name is a separate rule with conditions of its own, and it never reaches an address
     * a provider vouched for — {@code AccountRetentionTest} is that half.
     */
    @Test
    @DisplayName("takes the answers, the invitations, the days and the proposals with it")
    void deletesEverythingAboutThePoll() {
        var id = Sample.offsite(service, clock);
        service.decline(id, Sample.JONAS, List.of(today().plusMonths(2)), "None of these work");
        assertThat(database.rowsIn("ballot_proposal")).isEqualTo(1);

        clock.advanceDays(40);
        assertThat(service.deleteExpiredPolls()).isEqualTo(1);

        assertThat(database.rowsIn("poll")).isZero();
        assertThat(database.rowsIn("poll_invitee")).isZero();
        assertThat(database.rowsIn("candidate_day")).isZero();
        assertThat(database.rowsIn("ballot")).isZero();
        assertThat(database.rowsIn("ballot_day")).isZero();
        assertThat(database.rowsIn("ballot_proposal")).isZero();
        assertThat(database.rowsIn("account")).isEqualTo(Sample.EVERYBODY.size());
    }

    private UUID pollClosingOn(LocalDate day) {
        var id = service.create("Poll closing " + day, Sample.ADA, Sample.TEAM);
        service.replaceCandidateDays(id, Caller.of(Sample.ADA), List.of(day));
        service.send(id, Caller.of(Sample.ADA));
        return id;
    }

    private PollState state(UUID id) {
        return service.poll(id, Sample.ADA).orElseThrow().state();
    }

    private LocalDate today() {
        return LocalDate.now(clock);
    }
}
