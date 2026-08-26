package io.binarycodes.whichday.poll.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
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
import io.binarycodes.whichday.people.domain.Person;
import io.binarycodes.whichday.poll.domain.Caller;
import io.binarycodes.whichday.poll.domain.PollState;

/**
 * The three places the service answers differently when there is no provider behind
 * it: who may read a poll, who may answer one, and who may change one.
 *
 * <p>The people are {@link Sample}'s, addresses and all. What they are here is
 * strangers to each other — no directory, no invitations — so the fixture is
 * deliberately thin: a poll with an organizer and nobody else on it, which is what
 * this mode actually produces.
 */
@AnonymousWhichdayTest
@DisplayName("Counting a poll nobody signed in for")
class AnonymousPollServiceTest {

    @Autowired
    private TestClock clock;

    @Autowired
    private TestDatabase database;

    @Autowired
    private PollService service;

    private UUID poll;
    private LocalDate day;

    @BeforeEach
    void setUp() {
        database.empty();
        clock.reset();
        day = Sample.mondayAfterNext(LocalDate.now(clock));
        poll = service.create("Q3 offsite", Sample.ADA, List.of(Sample.ADA));
        service.replaceCandidateDays(poll, Caller.of(Sample.ADA), List.of(day));
        service.send(poll, Caller.of(Sample.ADA));
    }

    @Test
    @DisplayName("gives every new poll six digits, leading zeros and all")
    void everyPollGetsACode() {
        assertThat(service.adminCodeOf(poll)).get().asString().matches("\\d{6}");
    }

    /**
     * The one read a code widens. A draft has been shown to nobody, so the link cannot
     * have decided who may look — and the address that made it belongs to a session that
     * closing the tab ends. Without this, a code written down from the share screen is
     * worth nothing until the poll is opened, which is the one thing the person reading
     * that screen has not done yet.
     */
    @Test
    @DisplayName("opens a draft to the code, and to nothing else")
    void theCodeOpensADraft() {
        var draft = service.create("Not sent yet", Sample.ADA, List.of(Sample.ADA));
        service.replaceCandidateDays(draft, Caller.of(Sample.ADA), List.of(day));
        var code = service.adminCodeOf(draft).orElseThrow();

        assertThat(service.poll(draft, Sample.MIRO)).isEmpty();
        assertThat(service.poll(draft, Caller.of(Sample.MIRO, Optional.of("000000")))).isEmpty();
        assertThat(service.poll(draft, Caller.of(Sample.MIRO, Optional.of(code)))).isPresent();
        // Still the organizer's own, by address, with no code at all.
        assertThat(service.poll(draft, Sample.ADA)).isPresent();
    }

    /**
     * The whole bargain of the mode. In login mode this same call answers empty, and
     * {@code PollServiceTest.aStrangerSeesNothing} is the other half of the pair.
     */
    @Test
    @DisplayName("shows the poll to anybody holding its id")
    void theLinkIsTheCredential() {
        assertThat(service.poll(poll, Sample.TANVI)).isPresent();
    }

    /** A draft has been shown to nobody, so nobody has a link to it. */
    @Test
    @DisplayName("keeps a draft to the person putting it together")
    void aDraftIsStillPrivate() {
        var draft = service.create("Not sent yet", Sample.ADA, List.of(Sample.ADA));

        assertThat(service.poll(draft, Sample.ADA)).isPresent();
        assertThat(service.poll(draft, Sample.TANVI)).isEmpty();
    }

    /**
     * Answering is what puts somebody on the poll, because nothing else could: the
     * tallies, the avatar stacks and {@code Poll.awaiting} all read the invitee list,
     * so a ballot from somebody off it would be counted nowhere.
     */
    @Test
    @DisplayName("puts a voter on the poll as they answer it")
    void answeringJoinsThePoll() {
        service.castVote(poll, Sample.TANVI, Set.of(day));

        var answered = service.poll(poll, Sample.TANVI).orElseThrow();
        assertThat(answered.inviteCount()).isEqualTo(2);
        assertThat(answered.answerCount()).isEqualTo(1);
        assertThat(answered.tallies().getFirst().voteCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("counts a voter once, however many times they change their answer")
    void answeringTwiceIsStillOnePerson() {
        service.castVote(poll, Sample.TANVI, Set.of(day));
        service.castVote(poll, Sample.TANVI, Set.of());

        assertThat(service.poll(poll, Sample.TANVI).orElseThrow().inviteCount()).isEqualTo(2);
    }

    @Test
    @DisplayName("refuses to be changed by somebody with no code, and yields to the right one")
    void theCodeIsWhatChangesIt() {
        var code = service.adminCodeOf(poll).orElseThrow();

        assertThatThrownBy(() -> service.lock(poll, Caller.of(Sample.TANVI), day))
                .isInstanceOf(NotTheOrganizerException.class);
        assertThatThrownBy(() -> service.lock(poll, Caller.of(Sample.TANVI, Optional.of("000000")), day))
                .isInstanceOf(NotTheOrganizerException.class);

        service.lock(poll, Caller.of(Sample.TANVI, Optional.of(code)), day);

        assertThat(service.poll(poll, Sample.ADA).orElseThrow().state()).isEqualTo(PollState.LOCKED);
    }

    /**
     * Six digits are worth nothing without the link they go with, because the code is
     * only ever compared with the poll being changed — never looked up across the table.
     */
    @Test
    @DisplayName("takes another poll's six digits for nothing")
    void aCodeIsOnlyGoodForItsOwnPoll() {
        var elsewhere = service.create("Design review", Sample.MIRO, List.of(Sample.MIRO));
        var code = service.adminCodeOf(elsewhere).orElseThrow();

        assertThatThrownBy(() -> service.lock(poll, Caller.of(Sample.TANVI, Optional.of(code)), day))
                .isInstanceOf(NotTheOrganizerException.class);
    }

    /**
     * The organizer needs no code for their own poll: the address on it is theirs for
     * as long as the session that made it lasts.
     */
    @Test
    @DisplayName("still lets the person who called the poll change it without one")
    void theOrganizerNeedsNoCode() {
        service.lock(poll, Caller.of(Sample.ADA), day);

        assertThat(service.poll(poll, Sample.ADA).orElseThrow().lockedDay()).isEqualTo(day);
    }

    /**
     * Login mode withholds a poll's existence from a stranger; here the stranger is
     * looking at it, so a refusal that denied it existed would only read as a bug.
     */
    @Test
    @DisplayName("refuses a link-holder by name rather than pretending the poll is not there")
    void theRefusalSaysWhatItMeans() {
        assertThatThrownBy(() -> service.lock(poll, Caller.of(stranger()), day))
                .isInstanceOf(NotTheOrganizerException.class)
                .hasMessageContaining(stranger().email());
    }

    private static Person stranger() {
        return Person.signedIn("00000000-0000-0000-0000-000000000000-20260820t090000@whichday.anonymous",
                "Passer-by");
    }
}
