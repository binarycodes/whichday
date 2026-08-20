package io.binarycodes.whichday.poll.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import io.binarycodes.whichday.people.domain.Person;
import io.binarycodes.whichday.people.service.AccountDirectory;
import io.binarycodes.whichday.poll.domain.DayTally;
import io.binarycodes.whichday.poll.domain.PollState;
import io.binarycodes.whichday.poll.domain.PollSummary;

@DisplayName("Counting a poll")
class PollServiceTest {

    private static final Instant NOW = Instant.parse("2026-08-20T09:00:00Z");
    private static final String OFFSITE = "q3-team-offsite";

    private Clock clock;
    private AccountDirectory directory;
    private PollService service;

    @BeforeEach
    void setUp() {
        clock = Clock.fixed(NOW, ZoneOffset.UTC);
        directory = new AccountDirectory();
        service = new PollService(clock, directory);
    }

    @Test
    @DisplayName("reproduces the standings the design was drawn with")
    void sampleStandings() {
        var poll = service.poll(OFFSITE).orElseThrow();

        assertThat(poll.tallies()).extracting(DayTally::voteCount).containsExactly(6, 4, 3, 2, 1);
        assertThat(poll.inviteCount()).isEqualTo(7);
        assertThat(poll.answerCount()).isEqualTo(6);
        assertThat(poll.state()).isEqualTo(PollState.OPEN);
    }

    @Test
    @DisplayName("leaves exactly one person owed an answer, so the nudge has a name")
    void soleHoldout() {
        var poll = service.poll(OFFSITE).orElseThrow();

        assertThat(poll.soleHoldout()).map(Person::firstName).contains("Jonas");
        assertThat(poll.awaiting()).hasSize(1);
    }

    @Test
    @DisplayName("ranks by count and breaks a tie by date")
    void ranking() {
        var slug = service.create("Tie break", organizer(), directory.allForSwitcher());
        var monday = LocalDate.of(2026, 9, 7);
        var friday = LocalDate.of(2026, 9, 11);
        service.replaceCandidateDays(slug, List.of(friday, monday));
        service.castVote(slug, organizer(), Set.of(monday, friday));

        var tallies = service.poll(slug).orElseThrow().tallies();

        assertThat(tallies).extracting(DayTally::day).containsExactly(monday, friday);
        assertThat(tallies).extracting(DayTally::rank).containsExactly(1, 2);
    }

    @Test
    @DisplayName("fills a bar against everybody invited, not against the leader")
    void shareIsOfTheInvited() {
        var poll = service.poll(OFFSITE).orElseThrow();

        assertThat(poll.tallies().getFirst().share()).isEqualTo(6d / 7d);
        assertThat(poll.leader()).isPresent();
    }

    @Test
    @DisplayName("withdrawing a day drops the votes that were cast for it")
    void withdrawingADayDropsItsVotes() {
        var poll = service.poll(OFFSITE).orElseThrow();
        var remaining = poll.candidateDays().stream().skip(1).toList();

        service.replaceCandidateDays(OFFSITE, remaining);
        var updated = service.poll(OFFSITE).orElseThrow();

        assertThat(updated.candidateDays()).isEqualTo(remaining);
        assertThat(updated.tallies()).hasSize(remaining.size());
        assertThat(updated.ballots()).allSatisfy(ballot ->
                assertThat(ballot.chosenDays()).isSubsetOf(remaining));
    }

    @Test
    @DisplayName("ignores a vote for a day that is not on the table")
    void voteForAnUnofferedDay() {
        var poll = service.poll(OFFSITE).orElseThrow();
        var stranger = LocalDate.of(2027, 1, 1);

        service.castVote(OFFSITE, jonas(), Set.of(stranger));
        var updated = service.poll(OFFSITE).orElseThrow();

        assertThat(updated.tallies()).extracting(DayTally::day).doesNotContain(stranger);
        assertThat(updated.ballotOf(jonas())).isPresent();
    }

    @Test
    @DisplayName("counts somebody who can make none of the days as having answered")
    void decliningIsAnAnswer() {
        service.decline(OFFSITE, jonas(), List.of(LocalDate.of(2026, 9, 28)), "Away that week");
        var poll = service.poll(OFFSITE).orElseThrow();

        assertThat(poll.answerCount()).isEqualTo(7);
        assertThat(poll.awaiting()).isEmpty();
        assertThat(poll.declined()).singleElement().satisfies(ballot -> {
            assertThat(ballot.voter()).isEqualTo(jonas());
            assertThat(ballot.isDeclined()).isTrue();
            assertThat(ballot.note()).isEqualTo("Away that week");
        });
    }

    @Test
    @DisplayName("a proposal becomes a column only once it is accepted")
    void acceptingAProposal() {
        var proposed = LocalDate.of(2026, 9, 28);
        service.decline(OFFSITE, jonas(), List.of(proposed), null);

        assertThat(service.poll(OFFSITE).orElseThrow().candidateDays()).doesNotContain(proposed);

        service.acceptProposal(OFFSITE, proposed);

        assertThat(service.poll(OFFSITE).orElseThrow().candidateDays()).contains(proposed);
    }

    @Test
    @DisplayName("locking settles the poll and moves it out of the open list")
    void locking() {
        var leader = service.poll(OFFSITE).orElseThrow().leader().orElseThrow().day();

        service.lock(OFFSITE, leader);

        assertThat(service.poll(OFFSITE).orElseThrow().state()).isEqualTo(PollState.LOCKED);
        assertThat(service.openPolls(organizer())).extracting(PollSummary::slug).doesNotContain(OFFSITE);
        assertThat(service.settledPolls(organizer())).extracting(PollSummary::slug).contains(OFFSITE);
    }

    @Test
    @DisplayName("a new poll is a draft until it is sent, and then it is stamped")
    void sendingOpensThePoll() {
        var slug = service.create("Roadmap workshop", organizer(), directory.allForSwitcher());
        service.replaceCandidateDays(slug, List.of(LocalDate.of(2026, 9, 7)));

        assertThat(service.poll(slug).orElseThrow().state()).isEqualTo(PollState.DRAFT);

        service.send(slug);
        var sent = service.poll(slug).orElseThrow();

        assertThat(sent.state()).isEqualTo(PollState.OPEN);
        assertThat(sent.openedAt()).isEqualTo(NOW);
        assertThat(sent.closesAt().getDayOfWeek()).isEqualTo(java.time.DayOfWeek.FRIDAY);
    }

    @Test
    @DisplayName("sending twice keeps the original closing date")
    void sendingIsIdempotent() {
        var slug = service.create("Roadmap workshop", organizer(), directory.allForSwitcher());
        service.replaceCandidateDays(slug, List.of(LocalDate.of(2026, 9, 7)));
        service.send(slug);
        var first = service.poll(slug).orElseThrow().closesAt();

        service.send(slug);

        assertThat(service.poll(slug).orElseThrow().closesAt()).isEqualTo(first);
    }

    @Test
    @DisplayName("builds a readable slug, and keeps two polls of the same name apart")
    void slugs() {
        assertThat(service.create("Q4 review!", organizer(), directory.allForSwitcher())).isEqualTo("q4-review");

        var second = service.create("Q4 review!", organizer(), directory.allForSwitcher());

        assertThat(second).startsWith("q4-review-").isNotEqualTo("q4-review");
    }

    @Test
    @DisplayName("a poll with no usable name still gets a slug")
    void slugForANamelessPoll() {
        assertThat(service.create("!!!", organizer(), directory.allForSwitcher())).isEqualTo("poll");
    }

    @Test
    @DisplayName("reports whether the viewer has answered")
    void answeredByViewer() {
        var open = service.openPolls(organizer());

        assertThat(open).filteredOn(summary -> summary.slug().equals(OFFSITE))
                .singleElement()
                .satisfies(summary -> {
                    assertThat(summary.answeredByViewer()).isTrue();
                    assertThat(summary.hasHeadlineDay()).isTrue();
                    assertThat(summary.askedBy()).isEqualTo(organizer());
                });
        assertThat(open).filteredOn(summary -> !summary.slug().equals(OFFSITE))
                .allSatisfy(summary -> assertThat(summary.answeredByViewer()).isFalse());
    }

    @Test
    @DisplayName("a poll nobody has answered still names the month its days fall in")
    void undecidedPollNamesItsMonth() {
        var review = service.openPolls(organizer()).stream()
                .filter(summary -> summary.slug().equals("design-review-week"))
                .findFirst()
                .orElseThrow();

        assertThat(review.hasHeadlineDay()).isFalse();
        assertThat(review.firstCandidateDay()).isNotNull();
        assertThat(review.candidateDayCount()).isEqualTo(4);
    }

    @Test
    @DisplayName("refuses to work on a poll that is not there")
    void unknownPoll() {
        assertThat(service.poll("nope")).isEmpty();
        assertThatThrownBy(() -> service.lock("nope", LocalDate.of(2026, 9, 7)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("nope");
    }

    private Person organizer() {
        return directory.byEmail("ada.lindqvist@acme.com").orElseThrow();
    }

    private Person jonas() {
        return directory.byEmail("jonas.wirtanen@acme.com").orElseThrow();
    }
}
