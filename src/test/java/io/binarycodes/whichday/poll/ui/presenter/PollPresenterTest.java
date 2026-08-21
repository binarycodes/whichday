package io.binarycodes.whichday.poll.ui.presenter;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import io.binarycodes.whichday.Sample;
import io.binarycodes.whichday.TestClock;
import io.binarycodes.whichday.TestDatabase;
import io.binarycodes.whichday.WhichdayTest;
import io.binarycodes.whichday.people.domain.Person;
import io.binarycodes.whichday.people.service.AccountDirectory;
import io.binarycodes.whichday.people.ui.presenter.ViewerSession;
import io.binarycodes.whichday.poll.domain.Ballot;
import io.binarycodes.whichday.poll.domain.PollState;
import io.binarycodes.whichday.poll.domain.PollSummary;
import io.binarycodes.whichday.poll.service.InviteeSearch;
import io.binarycodes.whichday.poll.service.PollService;

@WhichdayTest
@DisplayName("What the screens ask the presenter")
class PollPresenterTest {

    @Autowired
    private TestClock clock;

    @Autowired
    private TestDatabase database;

    @Autowired
    private AccountDirectory directory;

    @Autowired
    private PollService polls;

    @Autowired
    private InviteeSearch invitees;

    private PollPresenter presenter;
    private Person signedIn;
    private UUID offsite;

    /**
     * The presenter is hand-built rather than injected: it is session-scoped, so there
     * is no Vaadin session to resolve one from this early, and its {@code ViewerSession}
     * is the thing this class is testing against.
     */
    @BeforeEach
    void setUp() {
        database.empty();
        clock.reset();
        Sample.signedInBefore(directory);
        offsite = Sample.offsite(polls, clock);
        signedIn = Sample.ADA;
        presenter = new PollPresenter(polls, invitees, viewerSession(), clock);
    }

    /** Stands in for the signed-in user, which the application reads from the provider. */
    private ViewerSession viewerSession() {
        return new ViewerSession() {
            @Override
            public Person viewer() {
                return signedIn;
            }

            @Override
            public void signOut() {
                signedIn = null;
            }
        };
    }

    @Test
    @DisplayName("reports the signed-in person and the clock it was given")
    void defaults() {
        assertThat(presenter.viewer()).isEqualTo(Sample.ADA);
        assertThat(presenter.today()).isEqualTo(LocalDate.of(2026, 8, 20));
        assertThat(presenter.instant()).isEqualTo(TestClock.START);
    }

    @Test
    @DisplayName("counts only the polls the signed-in person still owes an answer to")
    void awaitingViewer() {
        Sample.unanswered(polls, clock);

        assertThat(presenter.awaitingViewer()).isEqualTo(1);

        signedIn = Sample.JONAS;

        assertThat(presenter.awaitingViewer()).isEqualTo(2);
    }

    @Test
    @DisplayName("attributes a vote to whoever is signed in")
    void votesAsTheViewer() {
        signedIn = Sample.JONAS;
        var leader = presenter.poll(offsite).orElseThrow().leader().orElseThrow().day();

        presenter.vote(offsite, Set.of(leader));

        assertThat(presenter.ballotOf(offsite)).map(Ballot::voter).contains(Sample.JONAS);
        assertThat(presenter.poll(offsite).orElseThrow().awaiting()).isEmpty();
    }

    @Test
    @DisplayName("carries a new poll from a name to a sent poll")
    void createChooseSend() {
        var id = draftPoll("Roadmap workshop", List.of(Sample.MIRO));
        var monday = Sample.mondayAfterNext(LocalDate.now(clock));

        presenter.chooseDays(id, Set.of(monday));
        assertThat(presenter.poll(id).orElseThrow().state()).isEqualTo(PollState.DRAFT);

        presenter.send(id);
        var poll = presenter.poll(id).orElseThrow();

        assertThat(poll.state()).isEqualTo(PollState.OPEN);
        assertThat(poll.organizer()).isEqualTo(Sample.ADA);
        assertThat(presenter.isOrganizer(poll)).isTrue();
        assertThat(poll.inviteCount()).isEqualTo(2);
    }

    @Test
    @DisplayName("leads with the organizer, then keeps the order people were added in")
    void theOrganizerLeadsTheInvitedList() {
        var id = draftPoll("Roadmap workshop", List.of(Sample.LENA, Sample.MIRO));

        assertThat(presenter.poll(id).orElseThrow().invited())
                .containsExactly(Sample.ADA, Sample.LENA, Sample.MIRO);
    }

    @Test
    @DisplayName("counts the organizer once even if their own draft named them")
    void theOrganizerIsNeverCountedTwice() {
        var id = draftPoll("Roadmap workshop", List.of(Sample.ADA, Sample.ADA));

        assertThat(presenter.poll(id).orElseThrow().invited()).containsExactly(Sample.ADA);
    }

    @Test
    @DisplayName("invites an address with no account behind it like anybody else")
    void invitesAnOutsider() {
        var outsider = presenter.inviteeFor("lena.ohlsson@studiofern.se");

        var id = draftPoll("Roadmap workshop", List.of(outsider));

        assertThat(presenter.poll(id).orElseThrow().invited()).containsExactly(Sample.ADA, outsider);
        assertThat(presenter.hasAccount("lena.ohlsson@studiofern.se")).isFalse();
    }

    @Test
    @DisplayName("records a decline with its counter-proposal, then accepts it")
    void declineThenAccept() {
        var proposed = LocalDate.of(2026, 9, 28);
        signedIn = Sample.JONAS;

        presenter.declineAll(offsite, List.of(proposed), "Away that week");

        assertThat(presenter.ballotOf(offsite)).get().satisfies(ballot -> {
            assertThat(ballot.isDeclined()).isTrue();
            assertThat(ballot.proposedDays()).containsExactly(proposed);
        });

        signedIn = Sample.ADA;
        presenter.acceptProposal(offsite, proposed);

        assertThat(presenter.poll(offsite).orElseThrow().candidateDays()).contains(proposed);
    }

    @Test
    @DisplayName("locking moves the poll from the open list to the settled one")
    void lock() {
        var leader = presenter.poll(offsite).orElseThrow().leader().orElseThrow().day();

        presenter.lock(offsite, leader);

        assertThat(presenter.openPolls()).extracting(PollSummary::id).doesNotContain(offsite);
        assertThat(presenter.settledPolls()).extracting(PollSummary::id).contains(offsite);
        assertThat(presenter.poll(offsite).orElseThrow().lockedDay()).isEqualTo(leader);
    }

    @Test
    @DisplayName("lists the signed-in person's own drafts and deletes them")
    void drafts() {
        var id = draftPoll("Roadmap workshop", List.of(Sample.MIRO));

        assertThat(presenter.draftPolls()).extracting(PollSummary::id).containsExactly(id);

        presenter.deleteDraft(id);

        assertThat(presenter.draftPolls()).isEmpty();
    }

    @Test
    @DisplayName("signs out through the session it was given")
    void signOut() {
        presenter.signOut();

        assertThat(signedIn).isNull();
    }

    @Test
    @DisplayName("has no ballot to report for a poll that is not there")
    void unknownPoll() {
        var nobodys = UUID.randomUUID();

        assertThat(presenter.poll(nobodys)).isEmpty();
        assertThat(presenter.ballotOf(nobodys)).isEmpty();
    }

    private UUID draftPoll(String title, List<Person> invitees) {
        presenter.draft().reset();
        presenter.draft().rename(title);
        invitees.forEach(presenter.draft()::invite);
        return presenter.createFromDraft();
    }
}
