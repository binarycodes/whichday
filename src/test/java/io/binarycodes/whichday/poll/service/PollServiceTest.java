package io.binarycodes.whichday.poll.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
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
import io.binarycodes.whichday.poll.domain.Caller;
import io.binarycodes.whichday.poll.domain.DayTally;
import io.binarycodes.whichday.poll.domain.Poll;
import io.binarycodes.whichday.poll.domain.PollState;
import io.binarycodes.whichday.poll.domain.PollSummary;

@WhichdayTest
@DisplayName("Counting a poll")
class PollServiceTest {

    @Autowired
    private TestClock clock;

    @Autowired
    private TestDatabase database;

    @Autowired
    private PollService service;

    @Autowired
    private AccountDirectory directory;

    private UUID offsite;

    /**
     * The accounts come first: a poll stores addresses, so the names on the standings
     * are the account table's and a fixture that skipped it would have a team of
     * nameless addresses.
     */
    @BeforeEach
    void setUp() {
        database.empty();
        clock.reset();
        Sample.signedInBefore(directory);
        offsite = Sample.offsite(service, clock);
    }

    @Test
    @DisplayName("reproduces the standings the design was drawn with")
    void sampleStandings() {
        var poll = poll(offsite).orElseThrow();

        assertThat(poll.tallies()).extracting(DayTally::voteCount).containsExactly(6, 4, 3, 2, 1);
        assertThat(poll.inviteCount()).isEqualTo(7);
        assertThat(poll.answerCount()).isEqualTo(6);
        assertThat(poll.state()).isEqualTo(PollState.OPEN);
    }

    @Test
    @DisplayName("leaves exactly one person owed an answer, so the nudge has a name")
    void soleHoldout() {
        var poll = poll(offsite).orElseThrow();

        assertThat(poll.awaiting()).containsExactly(Sample.JONAS);
        assertThat(poll.awaitingOthers(Sample.ADA)).containsExactly(Sample.JONAS);
    }

    @Test
    @DisplayName("never counts the person looking as somebody to chase")
    void aHoldoutIsNeverYourself() {
        var day = Sample.mondayAfterNext(LocalDate.now(clock));
        var id = openPoll(List.of(Sample.ADA, Sample.JONAS), day);
        service.castVote(id, Sample.JONAS, Set.of(day));

        var poll = poll(id).orElseThrow();

        assertThat(poll.awaiting()).containsExactly(Sample.ADA);
        assertThat(poll.awaitingOthers(Sample.ADA)).isEmpty();
    }

    /**
     * The order breaks by date so the list is stable. The rank does not: two days on
     * the same count are the same rank, because their bars are the same length and
     * painting one darker claims an order that is not there.
     */
    @Test
    @DisplayName("orders by count and breaks a tie by date, but ranks tied days alike")
    void ranking() {
        var monday = Sample.mondayAfterNext(LocalDate.now(clock));
        var friday = monday.plusDays(4);
        var id = service.create("Tie break", Sample.ADA, Sample.TEAM);
        service.replaceCandidateDays(id, Caller.of(Sample.ADA), List.of(friday, monday));
        service.send(id, Caller.of(Sample.ADA));
        service.castVote(id, Sample.ADA, Set.of(monday, friday));

        var tallies = poll(id).orElseThrow().tallies();

        assertThat(tallies).extracting(DayTally::day).containsExactly(monday, friday);
        assertThat(tallies).extracting(DayTally::rank).containsExactly(1, 1);
    }

    /**
     * A shared top is not a result. The application used to hand the earliest of the
     * tied days rank 1, call it the most popular on every ballot, and offer it as the
     * only day that could be locked — so the other tied days were unreachable.
     */
    @Test
    @DisplayName("names no leader when the highest count is shared")
    void aTieHasNoLeader() {
        var monday = Sample.mondayAfterNext(LocalDate.now(clock));
        var tuesday = monday.plusDays(1);
        var friday = monday.plusDays(4);
        var id = service.create("Tie break", Sample.ADA, Sample.TEAM);
        service.replaceCandidateDays(id, Caller.of(Sample.ADA), List.of(monday, tuesday, friday));
        service.send(id, Caller.of(Sample.ADA));
        service.castVote(id, Sample.ADA, Set.of(monday, tuesday, friday));
        service.castVote(id, Sample.MIRO, Set.of(monday, tuesday, friday));

        var poll = poll(id).orElseThrow();

        assertThat(poll.leader()).isEmpty();
        assertThat(poll.tallies()).noneMatch(DayTally::isLeading);
        assertThat(poll.tiedAtTheTop()).extracting(DayTally::day)
                .containsExactly(monday, tuesday, friday);

        // One more vote settles it, and the leader is the day that actually won.
        service.castVote(id, Sample.SARA, Set.of(tuesday));

        var settled = poll(id).orElseThrow();
        assertThat(settled.leader()).get().extracting(DayTally::day).isEqualTo(tuesday);
        assertThat(settled.tiedAtTheTop()).isEmpty();
    }

    @Test
    @DisplayName("is not a tie when nobody has voted at all")
    void nobodyVotingIsNotATie() {
        var monday = Sample.mondayAfterNext(LocalDate.now(clock));
        var id = service.create("Nothing yet", Sample.ADA, Sample.TEAM);
        service.replaceCandidateDays(id, Caller.of(Sample.ADA), List.of(monday, monday.plusDays(1)));
        service.send(id, Caller.of(Sample.ADA));

        var poll = poll(id).orElseThrow();

        assertThat(poll.leader()).isEmpty();
        assertThat(poll.tiedAtTheTop()).isEmpty();
    }

    @Test
    @DisplayName("fills a bar against everybody invited, not against the leader")
    void shareIsOfTheInvited() {
        var poll = poll(offsite).orElseThrow();

        assertThat(poll.tallies().getFirst().share()).isEqualTo(6d / 7d);
        assertThat(poll.leader()).isPresent();
    }

    @Test
    @DisplayName("withdrawing a day drops the votes that were cast for it")
    void withdrawingADayDropsItsVotes() {
        var remaining = poll(offsite).orElseThrow().candidateDays().stream().skip(1).toList();

        service.replaceCandidateDays(offsite, Caller.of(Sample.ADA), remaining);
        var updated = poll(offsite).orElseThrow();

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
        var updated = poll(offsite).orElseThrow();

        assertThat(updated.tallies()).extracting(DayTally::day).doesNotContain(stranger);
        assertThat(updated.ballotOf(Sample.JONAS)).isPresent();
    }

    @Test
    @DisplayName("counts somebody who can make none of the days as having answered")
    void decliningIsAnAnswer() {
        service.decline(offsite, Sample.JONAS, List.of(LocalDate.of(2026, 9, 28)));
        var poll = poll(offsite).orElseThrow();

        assertThat(poll.answerCount()).isEqualTo(7);
        assertThat(poll.awaiting()).isEmpty();
        assertThat(poll.declined()).singleElement().satisfies(ballot -> {
            assertThat(ballot.voter()).isEqualTo(Sample.JONAS);
            assertThat(ballot.isDeclined()).isTrue();
        });
    }

    @Test
    @DisplayName("a proposal becomes a column only once it is accepted")
    void acceptingAProposal() {
        var proposed = LocalDate.of(2026, 9, 28);
        service.decline(offsite, Sample.JONAS, List.of(proposed));

        assertThat(poll(offsite).orElseThrow().candidateDays()).doesNotContain(proposed);

        service.acceptProposal(offsite, Caller.of(Sample.ADA), proposed);

        assertThat(poll(offsite).orElseThrow().candidateDays()).contains(proposed);
    }

    @Test
    @DisplayName("takes other days forward by default, and stops when the organizer says so")
    void alternativesAreTheOrganizersChoice() {
        assertThat(poll(offsite).orElseThrow().alternativesAllowed()).isTrue();

        service.allowAlternatives(offsite, Caller.of(Sample.ADA), false);

        assertThat(poll(offsite).orElseThrow().alternativesAllowed()).isFalse();
    }

    @Test
    @DisplayName("still lets somebody say none of the days work when alternatives are off")
    void decliningSurvivesAlternativesBeingOff() {
        service.allowAlternatives(offsite, Caller.of(Sample.ADA), false);

        service.decline(offsite, Sample.JONAS, List.of());
        var poll = poll(offsite).orElseThrow();

        assertThat(poll.answerCount()).isEqualTo(7);
        assertThat(poll.declined()).singleElement().satisfies(ballot -> {
            assertThat(ballot.isDeclined()).isTrue();
            assertThat(ballot.proposedDays()).isEmpty();
        });
    }

    @Test
    @DisplayName("locking settles the poll and moves it out of the open list")
    void locking() {
        var leader = poll(offsite).orElseThrow().leader().orElseThrow().day();

        service.lock(offsite, Caller.of(Sample.ADA), leader);

        assertThat(poll(offsite).orElseThrow().state()).isEqualTo(PollState.LOCKED);
        assertThat(service.openPolls(Sample.ADA)).extracting(PollSummary::id).doesNotContain(offsite);
        assertThat(service.settledPolls(Sample.ADA)).extracting(PollSummary::id).contains(offsite);
    }

    @Test
    @DisplayName("a new poll is a draft until it is sent, and then it is stamped")
    void sendingOpensThePoll() {
        var id = service.create("Roadmap workshop", Sample.ADA, Sample.TEAM);
        service.replaceCandidateDays(id, Caller.of(Sample.ADA), List.of(LocalDate.of(2026, 9, 7)));

        assertThat(poll(id).orElseThrow().state()).isEqualTo(PollState.DRAFT);

        service.send(id, Caller.of(Sample.ADA));
        var sent = poll(id).orElseThrow();

        assertThat(sent.state()).isEqualTo(PollState.OPEN);
        assertThat(sent.openedAt()).isEqualTo(TestClock.START);
    }

    @Test
    @DisplayName("sending twice keeps the original closing date")
    void sendingIsIdempotent() {
        var id = service.create("Roadmap workshop", Sample.ADA, Sample.TEAM);
        service.replaceCandidateDays(id, Caller.of(Sample.ADA), List.of(LocalDate.of(2026, 9, 7)));
        service.send(id, Caller.of(Sample.ADA));
        var first = poll(id).orElseThrow().closesOn();

        service.send(id, Caller.of(Sample.ADA));

        assertThat(poll(id).orElseThrow().closesOn()).isEqualTo(first);
    }

    @Test
    @DisplayName("runs to the last day on the table, that day included")
    void closesOnTheLastCandidateDay() {
        var first = LocalDate.of(2026, 9, 7);
        var last = LocalDate.of(2026, 9, 18);

        var id = openPoll(Sample.TEAM, first, LocalDate.of(2026, 9, 9), last);

        assertThat(poll(id).orElseThrow().closesOn()).isEqualTo(last);
    }

    @Test
    @DisplayName("keeps taking answers after an earlier option has passed")
    void anEarlyOptionPassingDoesNotEndIt() {
        var first = LocalDate.now(clock).plusDays(2);
        var last = LocalDate.now(clock).plusWeeks(3);
        var id = openPoll(Sample.TEAM, first, last);

        clock.advanceDays(3);

        assertThat(LocalDate.now(clock)).isAfter(first);
        assertThat(poll(id).orElseThrow().state()).isEqualTo(PollState.OPEN);
        service.castVote(id, Sample.JONAS, Set.of(last));
        assertThat(poll(id).orElseThrow().answerCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("never closes after the last day on the table")
    void neverClosesAfterTheLastCandidateDay() {
        var last = LocalDate.of(2026, 8, 24);

        assertThat(poll(openPoll(Sample.TEAM, LocalDate.of(2026, 8, 21), last))
                .orElseThrow().closesOn()).isEqualTo(last);
    }

    @Test
    @DisplayName("closes on the last day itself when that day is tomorrow")
    void aPollWhoseLastDayIsImminent() {
        var tomorrow = LocalDate.now(clock).plusDays(1);

        var poll = poll(openPoll(Sample.TEAM, tomorrow)).orElseThrow();

        assertThat(poll.closesOn()).isEqualTo(tomorrow);
        assertThat(poll.state()).isEqualTo(PollState.OPEN);
    }

    @Test
    @DisplayName("takes the organizer's own closing date, inside the range one can be in")
    void theOrganizerChoosesTheClosingDate() {
        var last = LocalDate.now(clock).plusWeeks(2);
        var id = openPoll(Sample.TEAM, LocalDate.now(clock).plusWeeks(1), last);

        service.closeOn(id, Caller.of(Sample.ADA), last.minusDays(4));
        assertThat(poll(id).orElseThrow().closesOn()).isEqualTo(last.minusDays(4));

        // Never past the last day on the table.
        service.closeOn(id, Caller.of(Sample.ADA), last.plusDays(3));
        assertThat(poll(id).orElseThrow().closesOn()).isEqualTo(last);

        // Never in the past.
        service.closeOn(id, Caller.of(Sample.ADA), LocalDate.now(clock).minusWeeks(1));
        assertThat(poll(id).orElseThrow().closesOn()).isEqualTo(LocalDate.now(clock).plusDays(1));

        assertThat(service.latestClosingDay(id)).contains(last);
    }

    @Test
    @DisplayName("promises the closing date a draft would get, before it has one")
    void plannedClosingBeforeSending() {
        var id = service.create("Roadmap workshop", Sample.ADA, Sample.TEAM);

        assertThat(service.plannedClosing(id)).isEmpty();

        service.replaceCandidateDays(id, Caller.of(Sample.ADA), List.of(LocalDate.of(2026, 9, 7), LocalDate.of(2026, 9, 9)));

        assertThat(service.plannedClosing(id)).contains(LocalDate.of(2026, 9, 9));

        service.send(id, Caller.of(Sample.ADA));

        assertThat(service.plannedClosing(id)).contains(poll(id).orElseThrow().closesOn());
    }

    @Test
    @DisplayName("stays open through its closing date and is closed the day after")
    void closesTheDayAfter() {
        var id = openPoll(Sample.TEAM, LocalDate.now(clock).plusWeeks(2));
        var closesOn = poll(id).orElseThrow().closesOn();

        clock.advanceDays(ChronoUnit.DAYS.between(LocalDate.now(clock), closesOn));
        assertThat(poll(id).orElseThrow().state()).isEqualTo(PollState.OPEN);

        clock.advanceDays(1);

        assertThat(poll(id).orElseThrow().state()).isEqualTo(PollState.CLOSED);
    }

    @Test
    @DisplayName("refuses an answer once voting is over")
    void refusesAnswersWhenClosed() {
        var id = openPoll(Sample.TEAM, LocalDate.now(clock).plusWeeks(2));
        var day = poll(id).orElseThrow().candidateDays().getFirst();
        var closesOn = poll(id).orElseThrow().closesOn();

        clock.advanceDays(ChronoUnit.DAYS.between(LocalDate.now(clock), closesOn) + 1);

        assertThatThrownBy(() -> service.castVote(id, Sample.JONAS, Set.of(day)))
                .isInstanceOf(PollClosedException.class)
                .hasMessageContaining(id.toString());
        assertThatThrownBy(() -> service.decline(id, Sample.JONAS, List.of()))
                .isInstanceOf(PollClosedException.class);
    }

    @Test
    @DisplayName("refuses an answer to a poll that was never sent")
    void refusesAnswersBeforeSending() {
        var id = service.create("Roadmap workshop", Sample.ADA, Sample.TEAM);
        service.replaceCandidateDays(id, Caller.of(Sample.ADA), List.of(LocalDate.of(2026, 9, 7)));

        assertThatThrownBy(() -> service.castVote(id, Sample.JONAS, Set.of(LocalDate.of(2026, 9, 7))))
                .isInstanceOf(PollClosedException.class);
    }

    @Test
    @DisplayName("keeps a draft out of the polls that are out with the team")
    void draftsAreListedApart() {
        var id = service.create("Roadmap workshop", Sample.ADA, Sample.TEAM);

        assertThat(service.openPolls(Sample.ADA)).extracting(PollSummary::id).doesNotContain(id);
        assertThat(service.settledPolls(Sample.ADA)).extracting(PollSummary::id).doesNotContain(id);
        assertThat(service.draftPolls(Sample.ADA)).extracting(PollSummary::id).containsExactly(id);
    }

    @Test
    @DisplayName("shows a draft to nobody but the person who named it")
    void draftsArePrivate() {
        var id = service.create("Roadmap workshop", Sample.ADA, Sample.TEAM);

        assertThat(service.draftPolls(Sample.JONAS)).extracting(PollSummary::id).doesNotContain(id);
        assertThat(service.draftPolls(Sample.ADA)).extracting(PollSummary::id).containsExactly(id);
    }

    @Test
    @DisplayName("discards a draft, and refuses to discard a poll that has gone out")
    void deletingDrafts() {
        var draft = service.create("Roadmap workshop", Sample.ADA, Sample.TEAM);

        service.deleteDraft(draft, Caller.of(Sample.ADA));

        assertThat(poll(draft)).isEmpty();
        assertThatThrownBy(() -> service.deleteDraft(offsite, Caller.of(Sample.ADA)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("cannot be discarded");
        assertThat(poll(offsite)).isPresent();
    }

    @Test
    @DisplayName("keeps two polls of the same name apart")
    void twoPollsOfTheSameName() {
        var first = service.create("Team event", Sample.ADA, Sample.TEAM);
        var second = service.create("Team event", Sample.ADA, Sample.TEAM);

        assertThat(second).isNotEqualTo(first);
        assertThat(poll(first)).isPresent();
        assertThat(poll(second)).isPresent();
    }

    @Test
    @DisplayName("an id nobody issued is not a poll")
    void anIdNobodyIssued() {
        assertThat(poll(UUID.randomUUID())).isEmpty();
    }

    @Test
    @DisplayName("reports whether the viewer has answered")
    void answeredByViewer() {
        Sample.unanswered(service, clock);

        assertThat(service.openPolls(Sample.ADA))
                .filteredOn(summary -> summary.id().equals(offsite))
                .singleElement()
                .satisfies(summary -> {
                    assertThat(summary.answeredByViewer()).isTrue();
                    assertThat(summary.hasHeadlineDay()).isTrue();
                    assertThat(summary.askedBy()).isEqualTo(Sample.ADA);
                });
        assertThat(service.openPolls(Sample.JONAS))
                .filteredOn(summary -> summary.id().equals(offsite))
                .singleElement()
                .satisfies(summary -> assertThat(summary.answeredByViewer()).isFalse());
    }

    @Test
    @DisplayName("a poll nobody has answered still names the month its days fall in")
    void undecidedPollNamesItsMonth() {
        var id = Sample.unanswered(service, clock);

        var review = service.openPolls(Sample.ADA).stream()
                .filter(summary -> summary.id().equals(id))
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
        var nobodys = UUID.randomUUID();

        assertThat(poll(nobodys)).isEmpty();
        assertThatThrownBy(() -> service.lock(nobodys, Caller.of(Sample.ADA), LocalDate.of(2026, 9, 7)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(nobodys.toString());
    }

    @Test
    @DisplayName("a poll is not there at all for somebody who was not invited")
    void aStrangerSeesNoPoll() {
        assertThat(service.poll(offsite, Sample.TANVI)).isEmpty();
        assertThat(poll(offsite)).isPresent();
    }

    @Test
    @DisplayName("refuses an answer from somebody who was not invited")
    void refusesAnUninvitedAnswer() {
        var day = poll(offsite).orElseThrow().candidateDays().getFirst();

        assertThatThrownBy(() -> service.castVote(offsite, Sample.TANVI, Set.of(day)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.decline(offsite, Sample.TANVI, List.of(day)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(poll(offsite).orElseThrow().answerCount()).isEqualTo(6);
    }

    @Test
    @DisplayName("tells an uninvited answer nothing an unknown id would not have told it")
    void refusalIsIndistinguishable() {
        var day = poll(offsite).orElseThrow().candidateDays().getFirst();
        var nobodys = UUID.randomUUID();

        var uninvited = catchThrowable(() -> service.castVote(offsite, Sample.TANVI, Set.of(day)));
        var unknown = catchThrowable(() -> service.castVote(nobodys, Sample.TANVI, Set.of(day)));

        assertThat(uninvited).hasSameClassAs(unknown);
        assertThat(uninvited.getMessage()).isEqualTo("No poll with id " + offsite);
        assertThat(unknown.getMessage()).isEqualTo("No poll with id " + nobodys);
    }

    @Test
    @DisplayName("lists only the polls the viewer is on")
    void listsOnlyYourOwnPolls() {
        Sample.settled(service, clock);
        // Organized by Miro, not by the openPoll helper, which would make Ada the organizer.
        var theirs = service.create("Theirs alone", Sample.MIRO, List.of(Sample.MIRO, Sample.JONAS));
        service.replaceCandidateDays(theirs, Caller.of(Sample.MIRO), List.of(LocalDate.now(clock).plusWeeks(2)));
        service.send(theirs, Caller.of(Sample.MIRO));

        assertThat(service.openPolls(Sample.ADA)).extracting(PollSummary::id).doesNotContain(theirs);
        assertThat(service.openPolls(Sample.MIRO)).extracting(PollSummary::id).contains(theirs);
        assertThat(service.openPolls(Sample.TANVI)).isEmpty();
        assertThat(service.settledPolls(Sample.TANVI)).isEmpty();
        assertThat(service.settledPolls(Sample.ADA)).isNotEmpty();
    }

    @Test
    @DisplayName("an organizer can read their own poll even if they are not on its invitee list")
    void theOrganizerCanAlwaysRead() {
        var id = service.create("Just for them", Sample.ADA, List.of(Sample.MIRO));

        assertThat(service.poll(id, Sample.ADA)).isPresent();
        assertThat(service.poll(id, Sample.TANVI)).isEmpty();
    }

    @Test
    @DisplayName("keeps a draft to its organizer, by id as well as in the list")
    void aDraftIsTheOrganizersAlone() {
        var draft = service.create("Not sent yet", Sample.ADA, Sample.TEAM);

        assertThat(service.poll(draft, Sample.ADA)).isPresent();
        assertThat(service.draftPolls(Sample.ADA)).extracting(PollSummary::id).contains(draft);

        // Miro is on the invitee list, which is why the list filter alone was not enough.
        assertThat(service.poll(draft, Sample.MIRO)).isEmpty();
        assertThat(service.draftPolls(Sample.MIRO)).isEmpty();

        // Sending it is what makes it everybody's to see.
        service.replaceCandidateDays(draft, Caller.of(Sample.ADA), List.of(Sample.mondayAfterNext(LocalDate.now(clock))));
        service.send(draft, Caller.of(Sample.ADA));
        assertThat(service.poll(draft, Sample.MIRO)).isPresent();
    }

    @Test
    @DisplayName("refuses every change to a poll whose closing date has passed")
    void nothingChangesOnceVotingIsOver() {
        var day = Sample.mondayAfterNext(LocalDate.now(clock));
        var id = openPoll(Sample.TEAM, day);
        service.castVote(id, Sample.MIRO, Set.of(day));
        var before = poll(id).orElseThrow();
        clock.advanceDays(ChronoUnit.DAYS.between(LocalDate.now(clock), before.closesOn()) + 1);
        assertThat(poll(id).orElseThrow().state()).isEqualTo(PollState.CLOSED);

        assertThatThrownBy(() -> service.replaceCandidateDays(id, Caller.of(Sample.ADA), List.of(day.plusWeeks(4))))
                .isInstanceOf(PollNotEditableException.class);
        assertThatThrownBy(() -> service.closeOn(id, Caller.of(Sample.ADA), day.plusWeeks(4)))
                .isInstanceOf(PollNotEditableException.class);
        assertThatThrownBy(() -> service.acceptProposal(id, Caller.of(Sample.ADA), day.plusWeeks(4)))
                .isInstanceOf(PollNotEditableException.class);
        assertThatThrownBy(() -> service.allowAlternatives(id, Caller.of(Sample.ADA), false))
                .isInstanceOf(PollNotEditableException.class);
        assertThatThrownBy(() -> service.addInvitee(id, Caller.of(Sample.ADA), Sample.TANVI))
                .isInstanceOf(PollNotEditableException.class);
        assertThatThrownBy(() -> service.lock(id, Caller.of(Sample.ADA), day))
                .isInstanceOf(PollNotEditableException.class);
        assertThatThrownBy(() -> service.send(id, Caller.of(Sample.ADA)))
                .isInstanceOf(PollNotEditableException.class);
        assertThatThrownBy(() -> service.deleteDraft(id, Caller.of(Sample.ADA)))
                .isInstanceOf(PollNotEditableException.class);
        // Answers were already refused, and say so in the voter's own terms.
        assertThatThrownBy(() -> service.castVote(id, Sample.SARA, Set.of(day)))
                .isInstanceOf(PollClosedException.class);

        var after = poll(id).orElseThrow();
        assertThat(after.state()).isEqualTo(PollState.CLOSED);
        assertThat(after.candidateDays()).isEqualTo(before.candidateDays());
        assertThat(after.closesOn()).isEqualTo(before.closesOn());
        assertThat(after.lockedDay()).isNull();
        assertThat(after.inviteCount()).isEqualTo(before.inviteCount());
        assertThat(after.alternativesAllowed()).isEqualTo(before.alternativesAllowed());
        assertThat(after.answerCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("refuses every change to a poll that has been settled")
    void nothingChangesOnceADayIsLocked() {
        var id = Sample.settled(service, clock);
        var settled = poll(id).orElseThrow();
        assertThat(settled.state()).isEqualTo(PollState.LOCKED);
        var other = settled.lockedDay().plusWeeks(3);

        assertThatThrownBy(() -> service.replaceCandidateDays(id, Caller.of(Sample.ADA), List.of(other)))
                .isInstanceOf(PollNotEditableException.class);
        assertThatThrownBy(() -> service.lock(id, Caller.of(Sample.ADA), other))
                .isInstanceOf(PollNotEditableException.class);
        assertThatThrownBy(() -> service.addInvitee(id, Caller.of(Sample.ADA), Sample.TANVI))
                .isInstanceOf(PollNotEditableException.class);
        assertThatThrownBy(() -> service.closeOn(id, Caller.of(Sample.ADA), other))
                .isInstanceOf(PollNotEditableException.class);

        var after = poll(id).orElseThrow();
        assertThat(after.lockedDay()).isEqualTo(settled.lockedDay());
        assertThat(after.candidateDays()).isEqualTo(settled.candidateDays());
        assertThat(after.inviteCount()).isEqualTo(settled.inviteCount());
    }

    @Test
    @DisplayName("does not let a closed poll tell a stranger it exists")
    void aClosedPollIsStillInvisible() {
        var id = openPoll(Sample.TEAM, LocalDate.now(clock).plusWeeks(2));
        var day = poll(id).orElseThrow().candidateDays().getFirst();
        clock.advanceDays(ChronoUnit.DAYS.between(LocalDate.now(clock), poll(id).orElseThrow().closesOn()) + 1);

        // An invitee is told the poll has closed, because they can see that it has.
        assertThatThrownBy(() -> service.castVote(id, Sample.MIRO, Set.of(day)))
                .isInstanceOf(PollClosedException.class);
        // A stranger is told nothing: "closed" would be a fact about a poll they should
        // not know is there.
        assertThatThrownBy(() -> service.castVote(id, Sample.TANVI, Set.of(day)))
                .isInstanceOf(IllegalArgumentException.class)
                .isNotInstanceOf(PollClosedException.class)
                .hasMessage("No poll with id " + id);
    }

    @Test
    @DisplayName("refuses every organizer action to somebody who is only invited")
    void onlyTheOrganizerMayChangeThePoll() {
        var poll = poll(offsite).orElseThrow();
        var day = poll.candidateDays().getFirst();
        var draft = service.create("Ada's draft", Sample.ADA, Sample.TEAM);

        // Miro is on this poll and can see all of it. None of it is his to change.
        assertThatThrownBy(() -> service.lock(offsite, Caller.of(Sample.MIRO), day))
                .isInstanceOf(NotTheOrganizerException.class);
        assertThatThrownBy(() -> service.acceptProposal(offsite, Caller.of(Sample.MIRO), day.plusDays(20)))
                .isInstanceOf(NotTheOrganizerException.class);
        assertThatThrownBy(() -> service.replaceCandidateDays(offsite, Caller.of(Sample.MIRO), List.of(day)))
                .isInstanceOf(NotTheOrganizerException.class);
        assertThatThrownBy(() -> service.closeOn(offsite, Caller.of(Sample.MIRO), day))
                .isInstanceOf(NotTheOrganizerException.class);
        assertThatThrownBy(() -> service.allowAlternatives(offsite, Caller.of(Sample.MIRO), false))
                .isInstanceOf(NotTheOrganizerException.class);
        assertThatThrownBy(() -> service.addInvitee(offsite, Caller.of(Sample.MIRO), Sample.TANVI))
                .isInstanceOf(NotTheOrganizerException.class);
        assertThatThrownBy(() -> service.send(draft, Caller.of(Sample.MIRO)))
                .isInstanceOf(NotTheOrganizerException.class);
        assertThatThrownBy(() -> service.deleteDraft(draft, Caller.of(Sample.MIRO)))
                .isInstanceOf(NotTheOrganizerException.class);

        // Nothing moved.
        var after = poll(offsite).orElseThrow();
        assertThat(after.state()).isEqualTo(PollState.OPEN);
        assertThat(after.lockedDay()).isNull();
        assertThat(after.candidateDays()).isEqualTo(poll.candidateDays());
        assertThat(after.inviteCount()).isEqualTo(poll.inviteCount());
        assertThat(after.alternativesAllowed()).isTrue();
        assertThat(service.poll(draft, Sample.ADA)).isPresent();
    }

    @Test
    @DisplayName("tells an invitee why, and tells a stranger nothing")
    void refusalsSayDifferentAmounts() {
        var day = poll(offsite).orElseThrow().candidateDays().getFirst();

        var invitee = catchThrowable(() -> service.lock(offsite, Caller.of(Sample.MIRO), day));
        var stranger = catchThrowable(() -> service.lock(offsite, Caller.of(Sample.TANVI), day));

        // Miro can see the poll, so there is nothing left to withhold — only to refuse.
        assertThat(invitee).isInstanceOf(NotTheOrganizerException.class)
                .hasMessageContaining(offsite.toString())
                .hasMessageContaining("not m.kallio@acme.com's to change");
        // Tanvi cannot, so she gets what an id nobody issued gets.
        assertThat(stranger).isInstanceOf(IllegalArgumentException.class)
                .hasMessage("No poll with id " + offsite);
    }

    @Test
    @DisplayName("still lets the organizer do all of it")
    void theOrganizerMayChangeThePoll() {
        var draft = service.create("Ada's own", Sample.ADA, Sample.TEAM);
        var day = Sample.mondayAfterNext(LocalDate.now(clock));

        service.replaceCandidateDays(draft, Caller.of(Sample.ADA), List.of(day));
        service.allowAlternatives(draft, Caller.of(Sample.ADA), false);
        service.addInvitee(draft, Caller.of(Sample.ADA), Sample.TANVI);
        service.send(draft, Caller.of(Sample.ADA));
        service.lock(draft, Caller.of(Sample.ADA), day);

        var settled = poll(draft).orElseThrow();
        assertThat(settled.state()).isEqualTo(PollState.LOCKED);
        assertThat(settled.lockedDay()).isEqualTo(day);
        assertThat(settled.alternativesAllowed()).isFalse();
        assertThat(settled.invited()).contains(Sample.TANVI);
    }

    /**
     * Reading a poll needs somebody to read it as, and Ada is on every fixture here —
     * she organizes most of them and is in {@code Sample.TEAM} for the rest.
     */
    private Optional<Poll> poll(UUID id) {
        return service.poll(id, Sample.ADA);
    }

    private UUID openPoll(List<Person> invited, LocalDate... days) {
        var id = service.create("Poll " + days[0], Sample.ADA, invited);
        service.replaceCandidateDays(id, Caller.of(Sample.ADA), List.of(days));
        service.send(id, Caller.of(Sample.ADA));
        return id;
    }
}
