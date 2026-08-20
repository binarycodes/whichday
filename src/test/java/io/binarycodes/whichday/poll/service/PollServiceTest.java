package io.binarycodes.whichday.poll.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import io.binarycodes.whichday.Sample;
import io.binarycodes.whichday.TestClock;
import io.binarycodes.whichday.people.domain.Person;
import io.binarycodes.whichday.poll.domain.DayTally;
import io.binarycodes.whichday.poll.domain.PollState;
import io.binarycodes.whichday.poll.domain.PollSummary;

@DisplayName("Counting a poll")
class PollServiceTest {

    private static final Instant NOW = Instant.parse("2026-08-20T09:00:00Z");

    private TestClock clock;
    private PollService service;
    private String offsite;

    @BeforeEach
    void setUp() {
        clock = new TestClock(NOW, ZoneOffset.UTC);
        service = new PollService(clock);
        offsite = Sample.offsite(service, clock);
    }

    @Test
    @DisplayName("reproduces the standings the design was drawn with")
    void sampleStandings() {
        var poll = service.poll(offsite).orElseThrow();

        assertThat(poll.tallies()).extracting(DayTally::voteCount).containsExactly(6, 4, 3, 2, 1);
        assertThat(poll.inviteCount()).isEqualTo(7);
        assertThat(poll.answerCount()).isEqualTo(6);
        assertThat(poll.state()).isEqualTo(PollState.OPEN);
    }

    @Test
    @DisplayName("leaves exactly one person owed an answer, so the nudge has a name")
    void soleHoldout() {
        var poll = service.poll(offsite).orElseThrow();

        assertThat(poll.awaiting()).containsExactly(Sample.JONAS);
        assertThat(poll.awaitingOthers(Sample.ADA)).containsExactly(Sample.JONAS);
    }

    @Test
    @DisplayName("never counts the person looking as somebody to chase")
    void aHoldoutIsNeverYourself() {
        var day = Sample.mondayAfterNext(LocalDate.now(clock));
        var slug = openPoll(List.of(Sample.ADA, Sample.JONAS), day);
        service.castVote(slug, Sample.JONAS, Set.of(day));

        var poll = service.poll(slug).orElseThrow();

        assertThat(poll.awaiting()).containsExactly(Sample.ADA);
        assertThat(poll.awaitingOthers(Sample.ADA)).isEmpty();
    }

    @Test
    @DisplayName("ranks by count and breaks a tie by date")
    void ranking() {
        var monday = Sample.mondayAfterNext(LocalDate.now(clock));
        var friday = monday.plusDays(4);
        var slug = service.create("Tie break", Sample.ADA, Sample.TEAM);
        service.replaceCandidateDays(slug, List.of(friday, monday));
        service.send(slug);
        service.castVote(slug, Sample.ADA, Set.of(monday, friday));

        var tallies = service.poll(slug).orElseThrow().tallies();

        assertThat(tallies).extracting(DayTally::day).containsExactly(monday, friday);
        assertThat(tallies).extracting(DayTally::rank).containsExactly(1, 2);
    }

    @Test
    @DisplayName("fills a bar against everybody invited, not against the leader")
    void shareIsOfTheInvited() {
        var poll = service.poll(offsite).orElseThrow();

        assertThat(poll.tallies().getFirst().share()).isEqualTo(6d / 7d);
        assertThat(poll.leader()).isPresent();
    }

    @Test
    @DisplayName("withdrawing a day drops the votes that were cast for it")
    void withdrawingADayDropsItsVotes() {
        var remaining = service.poll(offsite).orElseThrow().candidateDays().stream().skip(1).toList();

        service.replaceCandidateDays(offsite, remaining);
        var updated = service.poll(offsite).orElseThrow();

        assertThat(updated.candidateDays()).isEqualTo(remaining);
        assertThat(updated.tallies()).hasSize(remaining.size());
        assertThat(updated.ballots()).allSatisfy(ballot ->
                assertThat(ballot.chosenDays()).isSubsetOf(remaining));
    }

    @Test
    @DisplayName("ignores a vote for a day that is not on the table")
    void voteForAnUnofferedDay() {
        var stranger = LocalDate.of(2027, 1, 1);

        service.castVote(offsite, Sample.JONAS, Set.of(stranger));
        var updated = service.poll(offsite).orElseThrow();

        assertThat(updated.tallies()).extracting(DayTally::day).doesNotContain(stranger);
        assertThat(updated.ballotOf(Sample.JONAS)).isPresent();
    }

    @Test
    @DisplayName("counts somebody who can make none of the days as having answered")
    void decliningIsAnAnswer() {
        service.decline(offsite, Sample.JONAS, List.of(LocalDate.of(2026, 9, 28)), "Away that week");
        var poll = service.poll(offsite).orElseThrow();

        assertThat(poll.answerCount()).isEqualTo(7);
        assertThat(poll.awaiting()).isEmpty();
        assertThat(poll.declined()).singleElement().satisfies(ballot -> {
            assertThat(ballot.voter()).isEqualTo(Sample.JONAS);
            assertThat(ballot.isDeclined()).isTrue();
            assertThat(ballot.note()).isEqualTo("Away that week");
        });
    }

    @Test
    @DisplayName("a proposal becomes a column only once it is accepted")
    void acceptingAProposal() {
        var proposed = LocalDate.of(2026, 9, 28);
        service.decline(offsite, Sample.JONAS, List.of(proposed), null);

        assertThat(service.poll(offsite).orElseThrow().candidateDays()).doesNotContain(proposed);

        service.acceptProposal(offsite, proposed);

        assertThat(service.poll(offsite).orElseThrow().candidateDays()).contains(proposed);
    }

    @Test
    @DisplayName("takes other days forward by default, and stops when the organizer says so")
    void alternativesAreTheOrganizersChoice() {
        assertThat(service.poll(offsite).orElseThrow().alternativesAllowed()).isTrue();

        service.allowAlternatives(offsite, false);

        assertThat(service.poll(offsite).orElseThrow().alternativesAllowed()).isFalse();
    }

    @Test
    @DisplayName("still lets somebody say none of the days work when alternatives are off")
    void decliningSurvivesAlternativesBeingOff() {
        service.allowAlternatives(offsite, false);

        service.decline(offsite, Sample.JONAS, List.of(), "Away that week");
        var poll = service.poll(offsite).orElseThrow();

        assertThat(poll.answerCount()).isEqualTo(7);
        assertThat(poll.declined()).singleElement().satisfies(ballot -> {
            assertThat(ballot.isDeclined()).isTrue();
            assertThat(ballot.proposedDays()).isEmpty();
        });
    }

    @Test
    @DisplayName("locking settles the poll and moves it out of the open list")
    void locking() {
        var leader = service.poll(offsite).orElseThrow().leader().orElseThrow().day();

        service.lock(offsite, leader);

        assertThat(service.poll(offsite).orElseThrow().state()).isEqualTo(PollState.LOCKED);
        assertThat(service.openPolls(Sample.ADA)).extracting(PollSummary::slug).doesNotContain(offsite);
        assertThat(service.settledPolls(Sample.ADA)).extracting(PollSummary::slug).contains(offsite);
    }

    @Test
    @DisplayName("a new poll is a draft until it is sent, and then it is stamped")
    void sendingOpensThePoll() {
        var slug = service.create("Roadmap workshop", Sample.ADA, Sample.TEAM);
        service.replaceCandidateDays(slug, List.of(LocalDate.of(2026, 9, 7)));

        assertThat(service.poll(slug).orElseThrow().state()).isEqualTo(PollState.DRAFT);

        service.send(slug);
        var sent = service.poll(slug).orElseThrow();

        assertThat(sent.state()).isEqualTo(PollState.OPEN);
        assertThat(sent.openedAt()).isEqualTo(NOW);
    }

    @Test
    @DisplayName("sending twice keeps the original closing date")
    void sendingIsIdempotent() {
        var slug = service.create("Roadmap workshop", Sample.ADA, Sample.TEAM);
        service.replaceCandidateDays(slug, List.of(LocalDate.of(2026, 9, 7)));
        service.send(slug);
        var first = service.poll(slug).orElseThrow().closesOn();

        service.send(slug);

        assertThat(service.poll(slug).orElseThrow().closesOn()).isEqualTo(first);
    }

    @Test
    @DisplayName("runs to the last day on the table, that day included")
    void closesOnTheLastCandidateDay() {
        var first = LocalDate.of(2026, 9, 7);
        var last = LocalDate.of(2026, 9, 18);

        var slug = openPoll(Sample.TEAM, first, LocalDate.of(2026, 9, 9), last);

        assertThat(service.poll(slug).orElseThrow().closesOn()).isEqualTo(last);
    }

    @Test
    @DisplayName("keeps taking answers after an earlier option has passed")
    void anEarlyOptionPassingDoesNotEndIt() {
        var first = LocalDate.now(clock).plusDays(2);
        var last = LocalDate.now(clock).plusWeeks(3);
        var slug = openPoll(Sample.TEAM, first, last);

        clock.advanceDays(3);

        assertThat(LocalDate.now(clock)).isAfter(first);
        assertThat(service.poll(slug).orElseThrow().state()).isEqualTo(PollState.OPEN);
        service.castVote(slug, Sample.JONAS, Set.of(last));
        assertThat(service.poll(slug).orElseThrow().answerCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("never closes after the last day on the table")
    void neverClosesAfterTheLastCandidateDay() {
        var last = LocalDate.of(2026, 8, 24);

        assertThat(service.poll(openPoll(Sample.TEAM, LocalDate.of(2026, 8, 21), last))
                .orElseThrow().closesOn()).isEqualTo(last);
    }

    @Test
    @DisplayName("closes on the last day itself when that day is tomorrow")
    void aPollWhoseLastDayIsImminent() {
        var tomorrow = LocalDate.now(clock).plusDays(1);

        var poll = service.poll(openPoll(Sample.TEAM, tomorrow)).orElseThrow();

        assertThat(poll.closesOn()).isEqualTo(tomorrow);
        assertThat(poll.state()).isEqualTo(PollState.OPEN);
    }

    @Test
    @DisplayName("takes the organizer's own closing date, inside the range one can be in")
    void theOrganizerChoosesTheClosingDate() {
        var last = LocalDate.now(clock).plusWeeks(2);
        var slug = openPoll(Sample.TEAM, LocalDate.now(clock).plusWeeks(1), last);

        service.closeOn(slug, last.minusDays(4));
        assertThat(service.poll(slug).orElseThrow().closesOn()).isEqualTo(last.minusDays(4));

        // Never past the last day on the table.
        service.closeOn(slug, last.plusDays(3));
        assertThat(service.poll(slug).orElseThrow().closesOn()).isEqualTo(last);

        // Never in the past.
        service.closeOn(slug, LocalDate.now(clock).minusWeeks(1));
        assertThat(service.poll(slug).orElseThrow().closesOn()).isEqualTo(LocalDate.now(clock).plusDays(1));

        assertThat(service.latestClosingDay(slug)).contains(last);
    }

    @Test
    @DisplayName("promises the closing date a draft would get, before it has one")
    void plannedClosingBeforeSending() {
        var slug = service.create("Roadmap workshop", Sample.ADA, Sample.TEAM);

        assertThat(service.plannedClosing(slug)).isEmpty();

        service.replaceCandidateDays(slug, List.of(LocalDate.of(2026, 9, 7), LocalDate.of(2026, 9, 9)));

        assertThat(service.plannedClosing(slug)).contains(LocalDate.of(2026, 9, 9));

        service.send(slug);

        assertThat(service.plannedClosing(slug)).contains(service.poll(slug).orElseThrow().closesOn());
    }

    @Test
    @DisplayName("stays open through its closing date and is closed the day after")
    void closesTheDayAfter() {
        var slug = openPoll(Sample.TEAM, LocalDate.now(clock).plusWeeks(2));
        var closesOn = service.poll(slug).orElseThrow().closesOn();

        clock.advanceDays(ChronoUnit.DAYS.between(LocalDate.now(clock), closesOn));
        assertThat(service.poll(slug).orElseThrow().state()).isEqualTo(PollState.OPEN);

        clock.advanceDays(1);

        assertThat(service.poll(slug).orElseThrow().state()).isEqualTo(PollState.CLOSED);
    }

    @Test
    @DisplayName("refuses an answer once voting is over")
    void refusesAnswersWhenClosed() {
        var slug = openPoll(Sample.TEAM, LocalDate.now(clock).plusWeeks(2));
        var day = service.poll(slug).orElseThrow().candidateDays().getFirst();
        var closesOn = service.poll(slug).orElseThrow().closesOn();

        clock.advanceDays(ChronoUnit.DAYS.between(LocalDate.now(clock), closesOn) + 1);

        assertThatThrownBy(() -> service.castVote(slug, Sample.JONAS, Set.of(day)))
                .isInstanceOf(PollClosedException.class)
                .hasMessageContaining(slug);
        assertThatThrownBy(() -> service.decline(slug, Sample.JONAS, List.of(), null))
                .isInstanceOf(PollClosedException.class);
    }

    @Test
    @DisplayName("refuses an answer to a poll that was never sent")
    void refusesAnswersBeforeSending() {
        var slug = service.create("Roadmap workshop", Sample.ADA, Sample.TEAM);
        service.replaceCandidateDays(slug, List.of(LocalDate.of(2026, 9, 7)));

        assertThatThrownBy(() -> service.castVote(slug, Sample.JONAS, Set.of(LocalDate.of(2026, 9, 7))))
                .isInstanceOf(PollClosedException.class);
    }

    @Test
    @DisplayName("keeps a draft out of the polls that are out with the team")
    void draftsAreListedApart() {
        var slug = service.create("Roadmap workshop", Sample.ADA, Sample.TEAM);

        assertThat(service.openPolls(Sample.ADA)).extracting(PollSummary::slug).doesNotContain(slug);
        assertThat(service.settledPolls(Sample.ADA)).extracting(PollSummary::slug).doesNotContain(slug);
        assertThat(service.draftPolls(Sample.ADA)).extracting(PollSummary::slug).containsExactly(slug);
    }

    @Test
    @DisplayName("shows a draft to nobody but the person who named it")
    void draftsArePrivate() {
        var slug = service.create("Roadmap workshop", Sample.ADA, Sample.TEAM);

        assertThat(service.draftPolls(Sample.JONAS)).extracting(PollSummary::slug).doesNotContain(slug);
        assertThat(service.draftPolls(Sample.ADA)).extracting(PollSummary::slug).containsExactly(slug);
    }

    @Test
    @DisplayName("discards a draft, and refuses to discard a poll that has gone out")
    void deletingDrafts() {
        var draft = service.create("Roadmap workshop", Sample.ADA, Sample.TEAM);

        service.deleteDraft(draft);

        assertThat(service.poll(draft)).isEmpty();
        assertThatThrownBy(() -> service.deleteDraft(offsite))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("cannot be discarded");
        assertThat(service.poll(offsite)).isPresent();
    }

    @Test
    @DisplayName("builds a readable slug, and keeps two polls of the same name apart")
    void slugs() {
        assertThat(service.create("Q4 review!", Sample.ADA, Sample.TEAM)).isEqualTo("q4-review");

        var second = service.create("Q4 review!", Sample.ADA, Sample.TEAM);

        assertThat(second).startsWith("q4-review-").isNotEqualTo("q4-review");
    }

    @Test
    @DisplayName("a poll with no usable name still gets a slug")
    void slugForANamelessPoll() {
        assertThat(service.create("!!!", Sample.ADA, Sample.TEAM)).isEqualTo("poll");
    }

    @Test
    @DisplayName("reports whether the viewer has answered")
    void answeredByViewer() {
        Sample.unanswered(service, clock);

        assertThat(service.openPolls(Sample.ADA))
                .filteredOn(summary -> summary.slug().equals(offsite))
                .singleElement()
                .satisfies(summary -> {
                    assertThat(summary.answeredByViewer()).isTrue();
                    assertThat(summary.hasHeadlineDay()).isTrue();
                    assertThat(summary.askedBy()).isEqualTo(Sample.ADA);
                });
        assertThat(service.openPolls(Sample.JONAS))
                .filteredOn(summary -> summary.slug().equals(offsite))
                .singleElement()
                .satisfies(summary -> assertThat(summary.answeredByViewer()).isFalse());
    }

    @Test
    @DisplayName("a poll nobody has answered still names the month its days fall in")
    void undecidedPollNamesItsMonth() {
        var slug = Sample.unanswered(service, clock);

        var review = service.openPolls(Sample.ADA).stream()
                .filter(summary -> summary.slug().equals(slug))
                .findFirst()
                .orElseThrow();

        assertThat(review.hasHeadlineDay()).isFalse();
        assertThat(review.firstCandidateDay()).isNotNull();
        assertThat(review.candidateDayCount()).isEqualTo(4);
    }

    @Test
    @DisplayName("counts how often two people have decided a date together")
    void sharedHistory() {
        Sample.settled(service, clock);

        assertThat(service.pollsSharedBy(Sample.ADA, Sample.SARA)).isEqualTo(2);
        assertThat(service.pollsSharedBy(Sample.ADA, Sample.JONAS)).isEqualTo(1);
        assertThat(service.pollsSharedBy(Sample.ADA, Sample.TANVI)).isZero();
    }

    @Test
    @DisplayName("refuses to work on a poll that is not there")
    void unknownPoll() {
        assertThat(service.poll("nope")).isEmpty();
        assertThatThrownBy(() -> service.lock("nope", LocalDate.of(2026, 9, 7)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("nope");
    }

    private String openPoll(List<Person> invited, LocalDate... days) {
        var slug = service.create("Poll " + days[0], Sample.ADA, invited);
        service.replaceCandidateDays(slug, List.of(days));
        service.send(slug);
        return slug;
    }
}
