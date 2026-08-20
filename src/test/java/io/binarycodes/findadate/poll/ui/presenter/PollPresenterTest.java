package io.binarycodes.findadate.poll.ui.presenter;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import io.binarycodes.findadate.people.service.TeamDirectory;
import io.binarycodes.findadate.people.ui.presenter.ViewerSession;
import io.binarycodes.findadate.poll.domain.Ballot;
import io.binarycodes.findadate.poll.domain.PollState;
import io.binarycodes.findadate.poll.domain.PollSummary;
import io.binarycodes.findadate.poll.service.PollService;

@DisplayName("What the screens ask the presenter")
class PollPresenterTest {

    private static final Instant NOW = Instant.parse("2026-08-20T09:00:00Z");
    private static final String OFFSITE = "q3-team-offsite";

    private TeamDirectory directory;
    private ViewerSession session;
    private PollPresenter presenter;

    @BeforeEach
    void setUp() {
        var clock = Clock.fixed(NOW, ZoneOffset.UTC);
        directory = new TeamDirectory();
        session = new ViewerSession(directory);
        presenter = new PollPresenter(new PollService(clock, directory), session, clock);
    }

    @Test
    @DisplayName("starts as the organizer and reports the clock it was given")
    void defaults() {
        assertThat(presenter.viewer()).isEqualTo(directory.defaultViewer());
        assertThat(presenter.everyone()).hasSize(7);
        assertThat(presenter.teamName()).isEqualTo("Design team");
        assertThat(presenter.today()).isEqualTo(LocalDate.of(2026, 8, 20));
        assertThat(presenter.instant()).isEqualTo(NOW);
        assertThat(presenter.now().toLocalDate()).isEqualTo(LocalDate.of(2026, 8, 20));
    }

    @Test
    @DisplayName("counts only the polls the current viewer still owes an answer to")
    void awaitingViewerFollowsTheViewer() {
        assertThat(presenter.awaitingViewer()).isEqualTo(2);

        presenter.switchViewer(directory.byId("jonas").orElseThrow());

        assertThat(presenter.viewer().firstName()).isEqualTo("Jonas");
        assertThat(presenter.awaitingViewer()).isEqualTo(3);
    }

    @Test
    @DisplayName("attributes a vote to whoever is looking")
    void votesAsTheViewer() {
        var jonas = directory.byId("jonas").orElseThrow();
        presenter.switchViewer(jonas);
        var leader = presenter.poll(OFFSITE).orElseThrow().leader().orElseThrow().day();

        presenter.vote(OFFSITE, Set.of(leader));

        assertThat(presenter.ballotOf(OFFSITE)).map(Ballot::voter).contains(jonas);
        assertThat(presenter.poll(OFFSITE).orElseThrow().awaiting()).isEmpty();
    }

    @Test
    @DisplayName("carries a new poll from a name to a sent poll")
    void createChooseSend() {
        var slug = presenter.create("Roadmap workshop");
        var monday = LocalDate.of(2026, 9, 7);

        presenter.chooseDays(slug, Set.of(monday));
        assertThat(presenter.poll(slug).orElseThrow().state()).isEqualTo(PollState.DRAFT);

        presenter.send(slug);
        var poll = presenter.poll(slug).orElseThrow();

        assertThat(poll.state()).isEqualTo(PollState.OPEN);
        assertThat(poll.organizer()).isEqualTo(presenter.viewer());
        assertThat(presenter.isOrganizer(poll)).isTrue();
        assertThat(poll.inviteCount()).isEqualTo(7);
    }

    @Test
    @DisplayName("records a decline with its counter-proposal, then accepts it")
    void declineThenAccept() {
        var jonas = directory.byId("jonas").orElseThrow();
        var proposed = LocalDate.of(2026, 9, 28);
        presenter.switchViewer(jonas);

        presenter.declineAll(OFFSITE, List.of(proposed), "Away that week");

        assertThat(presenter.ballotOf(OFFSITE)).get().satisfies(ballot -> {
            assertThat(ballot.isDeclined()).isTrue();
            assertThat(ballot.proposedDays()).containsExactly(proposed);
        });

        presenter.switchViewer(directory.defaultViewer());
        presenter.acceptProposal(OFFSITE, proposed);

        assertThat(presenter.poll(OFFSITE).orElseThrow().candidateDays()).contains(proposed);
    }

    @Test
    @DisplayName("locking moves the poll from the open list to the settled one")
    void lock() {
        var leader = presenter.poll(OFFSITE).orElseThrow().leader().orElseThrow().day();

        presenter.lock(OFFSITE, leader);

        assertThat(presenter.openPolls()).extracting(PollSummary::slug).doesNotContain(OFFSITE);
        assertThat(presenter.settledPolls()).extracting(PollSummary::slug).contains(OFFSITE);
        assertThat(presenter.poll(OFFSITE).orElseThrow().lockedDay()).isEqualTo(leader);
    }

    @Test
    @DisplayName("has no ballot to report for a poll that is not there")
    void unknownPoll() {
        assertThat(presenter.poll("nope")).isEmpty();
        assertThat(presenter.ballotOf("nope")).isEmpty();
        assertThat(session.everyone()).isEqualTo(directory.members());
    }
}
