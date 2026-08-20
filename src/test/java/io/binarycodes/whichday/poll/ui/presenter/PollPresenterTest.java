package io.binarycodes.whichday.poll.ui.presenter;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import io.binarycodes.whichday.Sample;
import io.binarycodes.whichday.TestClock;
import io.binarycodes.whichday.people.domain.Person;
import io.binarycodes.whichday.people.service.AccountDirectory;
import io.binarycodes.whichday.people.ui.presenter.ViewerSession;
import io.binarycodes.whichday.poll.domain.Ballot;
import io.binarycodes.whichday.poll.domain.PollState;
import io.binarycodes.whichday.poll.domain.PollSummary;
import io.binarycodes.whichday.poll.service.InviteeSearch;
import io.binarycodes.whichday.poll.service.PollService;

@DisplayName("What the screens ask the presenter")
class PollPresenterTest {

    private static final Instant NOW = Instant.parse("2026-08-20T09:00:00Z");

    private TestClock clock;
    private AccountDirectory directory;
    private PollService polls;
    private PollPresenter presenter;
    private Person signedIn;
    private String offsite;

    @BeforeEach
    void setUp() {
        clock = new TestClock(NOW, ZoneOffset.UTC);
        directory = new AccountDirectory();
        Sample.signedInBefore(directory);
        polls = new PollService(clock);
        offsite = Sample.offsite(polls, clock);
        signedIn = Sample.ADA;
        presenter = new PollPresenter(polls, new InviteeSearch(directory, polls), viewerSession(), clock);
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
        assertThat(presenter.instant()).isEqualTo(NOW);
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
        var slug = draftPoll("Roadmap workshop", List.of(Sample.MIRO));
        var monday = Sample.mondayAfterNext(LocalDate.now(clock));

        presenter.chooseDays(slug, Set.of(monday));
        assertThat(presenter.poll(slug).orElseThrow().state()).isEqualTo(PollState.DRAFT);

        presenter.send(slug);
        var poll = presenter.poll(slug).orElseThrow();

        assertThat(poll.state()).isEqualTo(PollState.OPEN);
        assertThat(poll.organizer()).isEqualTo(Sample.ADA);
        assertThat(presenter.isOrganizer(poll)).isTrue();
        assertThat(poll.inviteCount()).isEqualTo(2);
    }

    @Test
    @DisplayName("leads with the organizer, then keeps the order people were added in")
    void theOrganizerLeadsTheInvitedList() {
        var slug = draftPoll("Roadmap workshop", List.of(Sample.LENA, Sample.MIRO));

        assertThat(presenter.poll(slug).orElseThrow().invited())
                .containsExactly(Sample.ADA, Sample.LENA, Sample.MIRO);
    }

    @Test
    @DisplayName("counts the organizer once even if their own draft named them")
    void theOrganizerIsNeverCountedTwice() {
        var slug = draftPoll("Roadmap workshop", List.of(Sample.ADA, Sample.ADA));

        assertThat(presenter.poll(slug).orElseThrow().invited()).containsExactly(Sample.ADA);
    }

    @Test
    @DisplayName("invites an address with no account behind it like anybody else")
    void invitesAnOutsider() {
        var outsider = presenter.inviteeFor("lena.ohlsson@studiofern.se");

        var slug = draftPoll("Roadmap workshop", List.of(outsider));

        assertThat(presenter.poll(slug).orElseThrow().invited()).containsExactly(Sample.ADA, outsider);
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

        assertThat(presenter.openPolls()).extracting(PollSummary::slug).doesNotContain(offsite);
        assertThat(presenter.settledPolls()).extracting(PollSummary::slug).contains(offsite);
        assertThat(presenter.poll(offsite).orElseThrow().lockedDay()).isEqualTo(leader);
    }

    @Test
    @DisplayName("lists the signed-in person's own drafts and deletes them")
    void drafts() {
        var slug = draftPoll("Roadmap workshop", List.of(Sample.MIRO));

        assertThat(presenter.draftPolls()).extracting(PollSummary::slug).containsExactly(slug);

        presenter.deleteDraft(slug);

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
        assertThat(presenter.poll("nope")).isEmpty();
        assertThat(presenter.ballotOf("nope")).isEmpty();
    }

    private String draftPoll(String title, List<Person> invitees) {
        presenter.draft().reset();
        presenter.draft().rename(title);
        invitees.forEach(presenter.draft()::invite);
        return presenter.createFromDraft();
    }
}
