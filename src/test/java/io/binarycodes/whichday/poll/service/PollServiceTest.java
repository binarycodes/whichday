package io.binarycodes.whichday.poll.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import io.binarycodes.whichday.TestClock;
import io.binarycodes.whichday.people.domain.Person;
import io.binarycodes.whichday.people.service.AccountDirectory;
import io.binarycodes.whichday.poll.domain.DayTally;
import io.binarycodes.whichday.poll.domain.PollState;
import io.binarycodes.whichday.poll.domain.PollSummary;

@DisplayName("Counting a poll")
class PollServiceTest {

    private static final Instant NOW = Instant.parse("2026-08-20T09:00:00Z");
    private static final String OFFSITE = "q3-team-offsite";

    private TestClock clock;
    private AccountDirectory directory;
    private PollService service;

    @BeforeEach
    void setUp() {
        clock = new TestClock(NOW, ZoneOffset.UTC);
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

        assertThat(poll.awaiting()).extracting(Person::firstName).containsExactly("Jonas");
        assertThat(poll.awaitingOthers(organizer())).extracting(Person::firstName)
                .containsExactly("Jonas");
    }

    @Test
    @DisplayName("never counts the person looking as somebody to chase")
    void aHoldoutIsNeverYourself() {
        var slug = service.create("Roadmap workshop", organizer(), List.of(organizer(), jonas()));
        service.replaceCandidateDays(slug, List.of(LocalDate.of(2026, 9, 7)));
        service.send(slug);
        service.castVote(slug, jonas(), Set.of(LocalDate.of(2026, 9, 7)));

        var poll = service.poll(slug).orElseThrow();

        assertThat(poll.awaiting()).containsExactly(organizer());
        assertThat(poll.awaitingOthers(organizer())).isEmpty();
    }

    @Test
    @DisplayName("ranks by count and breaks a tie by date")
    void ranking() {
        var slug = service.create("Tie break", organizer(), directory.allForSwitcher());
        var monday = LocalDate.of(2026, 9, 7);
        var friday = LocalDate.of(2026, 9, 11);
        service.replaceCandidateDays(slug, List.of(friday, monday));
        service.send(slug);
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
    @DisplayName("takes other days forward by default, and stops when the organizer says so")
    void alternativesAreTheOrganizersChoice() {
        assertThat(service.poll(OFFSITE).orElseThrow().alternativesAllowed()).isTrue();

        service.allowAlternatives(OFFSITE, false);

        assertThat(service.poll(OFFSITE).orElseThrow().alternativesAllowed()).isFalse();
    }

    @Test
    @DisplayName("still lets somebody say none of the days work when alternatives are off")
    void decliningSurvivesAlternativesBeingOff() {
        service.allowAlternatives(OFFSITE, false);

        service.decline(OFFSITE, jonas(), List.of(), "Away that week");
        var poll = service.poll(OFFSITE).orElseThrow();

        assertThat(poll.answerCount()).isEqualTo(7);
        assertThat(poll.declined()).singleElement().satisfies(ballot -> {
            assertThat(ballot.isDeclined()).isTrue();
            assertThat(ballot.proposedDays()).isEmpty();
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
    }

    @Test
    @DisplayName("closes at six on the last working day before the first day on the table")
    void closesBeforeTheFirstCandidateDay() {
        // Monday 7 September, so voting ends on Friday the 4th.
        var slug = openPollOn(LocalDate.of(2026, 9, 7), LocalDate.of(2026, 9, 8));

        assertThat(service.poll(slug).orElseThrow().closesOn()).isEqualTo(LocalDate.of(2026, 9, 4));
    }

    @Test
    @DisplayName("steps back over a weekend rather than closing on one")
    void closesOnAWorkingDay() {
        // Tuesday 8 September; the day before is a Monday, so no stepping needed.
        assertThat(service.poll(openPollOn(LocalDate.of(2026, 9, 8))).orElseThrow().closesOn())
                .isEqualTo(LocalDate.of(2026, 9, 7));

        // Monday 14 September; back over Sunday and Saturday to Friday the 11th.
        assertThat(service.poll(openPollOn(LocalDate.of(2026, 9, 14))).orElseThrow().closesOn())
                .isEqualTo(LocalDate.of(2026, 9, 11));
    }

    @Test
    @DisplayName("never closes after the day being voted on")
    void neverClosesAfterTheFirstCandidateDay() {
        var earliest = LocalDate.of(2026, 8, 24);

        var closes = service.poll(openPollOn(earliest)).orElseThrow().closesOn();

        assertThat(closes).isBefore(earliest);
    }

    @Test
    @DisplayName("closes on today when the first day on the table is tomorrow")
    void aPollWhoseFirstDayIsImminent() {
        var today = LocalDate.now(clock);

        var poll = service.poll(openPollOn(today.plusDays(1))).orElseThrow();

        assertThat(poll.closesOn()).isEqualTo(today);
        assertThat(poll.state()).isEqualTo(PollState.OPEN);
    }

    @Test
    @DisplayName("stays open through its closing date and is closed the day after")
    void closesTheDayAfter() {
        var slug = openPollOn(LocalDate.now(clock).plusWeeks(2));
        var closesOn = service.poll(slug).orElseThrow().closesOn();

        clock.advanceDays(ChronoUnit.DAYS.between(LocalDate.now(clock), closesOn));
        assertThat(LocalDate.now(clock)).isEqualTo(closesOn);
        assertThat(service.poll(slug).orElseThrow().state()).isEqualTo(PollState.OPEN);

        clock.advanceDays(1);

        assertThat(service.poll(slug).orElseThrow().state()).isEqualTo(PollState.CLOSED);
    }

    @Test
    @DisplayName("refuses an answer once voting is over")
    void refusesAnswersWhenClosed() {
        var slug = openPollOn(LocalDate.now(clock).plusWeeks(2));
        var day = service.poll(slug).orElseThrow().candidateDays().getFirst();
        var closesOn = service.poll(slug).orElseThrow().closesOn();

        clock.advanceDays(ChronoUnit.DAYS.between(LocalDate.now(clock), closesOn) + 1);

        assertThatThrownBy(() -> service.castVote(slug, jonas(), Set.of(day)))
                .isInstanceOf(PollClosedException.class)
                .hasMessageContaining(slug);
        assertThatThrownBy(() -> service.decline(slug, jonas(), List.of(), null))
                .isInstanceOf(PollClosedException.class);
    }

    @Test
    @DisplayName("refuses an answer to a poll that was never sent")
    void refusesAnswersBeforeSending() {
        var slug = service.create("Roadmap workshop", organizer(), directory.allForSwitcher());
        service.replaceCandidateDays(slug, List.of(LocalDate.of(2026, 9, 7)));

        assertThatThrownBy(() -> service.castVote(slug, jonas(), Set.of(LocalDate.of(2026, 9, 7))))
                .isInstanceOf(PollClosedException.class);
    }

    @Test
    @DisplayName("takes the organizer's own closing date, inside the range one can be in")
    void theOrganizerChoosesTheClosingDate() {
        var earliest = LocalDate.now(clock).plusWeeks(2);
        var slug = openPollOn(earliest);

        service.closeOn(slug, earliest.minusDays(4));
        assertThat(service.poll(slug).orElseThrow().closesOn()).isEqualTo(earliest.minusDays(4));

        // Never on or after the first day being voted on.
        service.closeOn(slug, earliest.plusDays(3));
        assertThat(service.poll(slug).orElseThrow().closesOn()).isEqualTo(earliest.minusDays(1));

        // Never in the past.
        service.closeOn(slug, LocalDate.now(clock).minusWeeks(1));
        assertThat(service.poll(slug).orElseThrow().closesOn()).isEqualTo(LocalDate.now(clock).plusDays(1));

        assertThat(service.latestClosingDay(slug)).contains(earliest.minusDays(1));
    }

    @Test
    @DisplayName("promises the closing time a draft would get, before it has one")
    void plannedClosingBeforeSending() {
        var slug = service.create("Roadmap workshop", organizer(), directory.allForSwitcher());

        assertThat(service.plannedClosing(slug)).isEmpty();

        service.replaceCandidateDays(slug, List.of(LocalDate.of(2026, 9, 7)));

        assertThat(service.plannedClosing(slug)).contains(LocalDate.of(2026, 9, 4));

        service.send(slug);

        assertThat(service.plannedClosing(slug)).contains(service.poll(slug).orElseThrow().closesOn());
    }

    private String openPollOn(LocalDate... days) {
        var slug = service.create("Poll " + days[0], organizer(), directory.allForSwitcher());
        service.replaceCandidateDays(slug, List.of(days));
        service.send(slug);
        return slug;
    }

    @Test
    @DisplayName("sending twice keeps the original closing date")
    void sendingIsIdempotent() {
        var slug = service.create("Roadmap workshop", organizer(), directory.allForSwitcher());
        service.replaceCandidateDays(slug, List.of(LocalDate.of(2026, 9, 7)));
        service.send(slug);
        var first = service.poll(slug).orElseThrow().closesOn();

        service.send(slug);

        assertThat(service.poll(slug).orElseThrow().closesOn()).isEqualTo(first);
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
