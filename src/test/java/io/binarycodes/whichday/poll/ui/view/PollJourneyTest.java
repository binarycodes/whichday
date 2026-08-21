package io.binarycodes.whichday.poll.ui.view;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;

import com.vaadin.browserless.SpringBrowserlessTest;
import com.vaadin.flow.component.ClickEvent;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.ComponentUtil;
import com.vaadin.flow.component.HasText;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.checkbox.Checkbox;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.NativeButton;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.router.RouteParameters;

import io.binarycodes.whichday.Application;
import io.binarycodes.whichday.Sample;
import io.binarycodes.whichday.StubIdentity;
import io.binarycodes.whichday.base.ui.Actions;
import io.binarycodes.whichday.base.ui.DateText;
import io.binarycodes.whichday.people.domain.Person;
import io.binarycodes.whichday.people.service.AccountDirectory;
import io.binarycodes.whichday.people.ui.AccountMenu;
import io.binarycodes.whichday.poll.domain.PollState;
import io.binarycodes.whichday.poll.domain.PollSummary;
import io.binarycodes.whichday.poll.service.PollService;
import io.binarycodes.whichday.poll.ui.component.DayBallot;
import io.binarycodes.whichday.poll.ui.component.DayPoster;
import io.binarycodes.whichday.poll.ui.component.MonthCalendar;
import io.binarycodes.whichday.poll.ui.presenter.PollPresenter;

/**
 * The whole journey without a browser: every screen builds, and the ones that change
 * state hand the next one something to show.
 *
 * <p>Every route requires an authenticated user, so {@link StubIdentity} says who the
 * browser is. Nothing seeds the store, so each test builds the polls it needs.
 */
@SpringBootTest(classes = Application.class)
@ActiveProfiles("test")
// A fresh context per method, because PollService is a singleton and nothing reseeds
// it: without this, polls built by one test are still in the store for the next. It
// is what makes this class slow, and the reason is worth more than the seconds.
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
@DisplayName("Walking the poll")
class PollJourneyTest extends SpringBrowserlessTest {

    @Autowired
    private ApplicationContext context;

    private UUID offsite;

    @BeforeEach
    void signIn() {
        StubIdentity.signIn(Sample.ADA);
        Sample.signedInBefore(context.getBean(AccountDirectory.class));
        offsite = Sample.offsite(context.getBean(PollService.class), clock());
    }

    @AfterEach
    void signOut() {
        StubIdentity.clear();
    }

    private Clock clock() {
        return context.getBean(Clock.class);
    }

    @Test
    @DisplayName("lists a draft on its own, between the live polls and the settled ones")
    void draftsHaveTheirOwnSection() {
        var id = draftPoll("Roadmap workshop");

        UI.getCurrent().navigate(PollsView.class);
        var screen = textOf(currentView());

        assertThat(screen).contains("Drafts", "Roadmap workshop", "No days chosen yet");
        assertThat(presenter().openPolls()).extracting(PollSummary::id).doesNotContain(id);
    }

    @Test
    @DisplayName("a draft is edited from the list, or deleted after it asks")
    void editingAndDeletingADraft() {
        var id = draftPoll("Roadmap workshop");
        UI.getCurrent().navigate(PollsView.class);

        click("Edit");
        assertThat(currentView()).isInstanceOf(CandidateDaysView.class);

        UI.getCurrent().navigate(PollsView.class);
        click("Delete");

        assertThat(textOf(currentView())).contains("Delete this draft?");
        assertThat(presenter().poll(id)).isPresent();

        click("Delete");

        assertThat(presenter().poll(id)).isEmpty();
        assertThat(textOf(currentView())).doesNotContain("Roadmap workshop");
    }

    @Test
    @DisplayName("opens on the poll list, with the offsite and the settled ones")
    void pollList() {
        Sample.settled(context.getBean(PollService.class), clock());
        UI.getCurrent().navigate(PollsView.class);

        assertThat(textOf(currentView())).contains("Q3 team offsite", "Settled");
    }

    @Test
    @DisplayName("shows the organizer the standings and the holdout")
    void results() {
        navigateToPoll(ResultsView.class, offsite);

        assertThat(textOf(currentView()))
                .contains("6 of 7", "have voted", "Everyone but Jonas", "Jonas");
    }

    @Test
    @DisplayName("keeps the account last in the header, after who invited you")
    void theAccountStaysOnTheRight() {
        StubIdentity.signIn(Sample.JONAS);
        navigateToPoll(BallotView.class, offsite);

        var header = componentsOf(currentView())
                .filter(Div.class::isInstance)
                .map(Div.class::cast)
                .filter(row -> row.getClassNames().contains("invitation"))
                .findFirst()
                .orElseThrow();

        assertThat(header.getChildren().toList()).last().isInstanceOf(AccountMenu.class);
    }

    @Test
    @DisplayName("offers a voter every day on the table")
    void ballot() {
        StubIdentity.signIn(Sample.JONAS);
        navigateToPoll(BallotView.class, offsite);

        assertThat(dayRows()).hasSize(5);
        assertThat(textOf(currentView())).contains("Tap every day that works.", "0 of 5 selected");
    }

    @Test
    @DisplayName("keeps the very button you tapped, so a keyboard caret survives a tap")
    void tappingDoesNotRebuildTheControl() {
        StubIdentity.signIn(Sample.JONAS);
        navigateToPoll(BallotView.class, offsite);
        var rowsBefore = dayRows();

        tap(rowsBefore.getFirst());

        assertThat(dayRows()).containsExactlyElementsOf(rowsBefore);
        assertThat(rowsBefore.getFirst().getElement().getAttribute("aria-pressed")).isEqualTo("true");
        assertThat(ballotField().getValue()).hasSize(1);
    }

    @Test
    @DisplayName("tapping days and submitting records a ballot and lands on the receipt")
    void votingRecordsABallot() {
        StubIdentity.signIn(Sample.JONAS);
        navigateToPoll(BallotView.class, offsite);
        var days = presenter().poll(offsite).orElseThrow().candidateDays();

        ballotField().setValue(Set.of(days.getFirst(), days.getLast()));
        click("Submit my days");

        assertThat(presenter().ballotOf(offsite)).get().satisfies(ballot ->
                assertThat(ballot.chosenDays()).containsExactlyInAnyOrder(days.getFirst(), days.getLast()));
        assertThat(currentView()).isInstanceOf(ReceiptView.class);
        assertThat(presenter().poll(offsite).orElseThrow().awaiting()).isEmpty();
    }

    @Test
    @DisplayName("refuses an empty ballot rather than recording one")
    void anEmptyBallotIsNotAnAnswer() {
        StubIdentity.signIn(Sample.JONAS);
        navigateToPoll(BallotView.class, offsite);

        click("Submit my days");

        assertThat(presenter().ballotOf(offsite)).isEmpty();
        assertThat(currentView()).isInstanceOf(BallotView.class);
    }

    @Test
    @DisplayName("reads back the answer a voter already gave")
    void receipt() {
        navigateToPoll(ReceiptView.class, offsite);

        assertThat(textOf(currentView())).contains("Your answer is in", "Where the team stands");
    }

    @Test
    @DisplayName("shows a poster per day you chose, and a bar per day on the table")
    void theReceiptHidesNoDays() {
        StubIdentity.signIn(Sample.JONAS);
        var days = presenter().poll(offsite).orElseThrow().candidateDays();
        presenter().vote(offsite, Set.copyOf(days));

        navigateToPoll(ReceiptView.class, offsite);
        var screen = textOf(currentView());

        assertThat(screen).contains("You said yes to " + days.size() + " days");
        assertThat(componentsOf(currentView()).filter(DayPoster.class::isInstance)).hasSize(days.size());
        days.forEach(day -> assertThat(screen).contains(DateText.compact(currentView(), day)));
    }

    @Test
    @DisplayName("lets the organizer answer their own poll, and comes back to the counts")
    void theOrganizerCanVote() {
        var id = openPoll("Roadmap workshop");
        navigateToPoll(ResultsView.class, id);

        assertThat(textOf(currentView())).contains("You haven't picked your days yet.");

        click("Pick mine");
        assertThat(currentView()).isInstanceOf(BallotView.class);

        ballotField().setValue(Set.copyOf(presenter().poll(id).orElseThrow().candidateDays()));
        click("Submit my days");

        assertThat(currentView()).isInstanceOf(ResultsView.class);
        assertThat(presenter().ballotOf(id)).isPresent();
    }

    @Test
    @DisplayName("never offers the organizer a nudge to themselves")
    void theOrganizerIsNeverNudged() {
        var id = openPoll("Roadmap workshop");
        var day = presenter().poll(id).orElseThrow().candidateDays().getFirst();
        StubIdentity.signIn(Sample.MIRO);
        presenter().vote(id, Set.of(day));
        StubIdentity.signIn(Sample.ADA);

        navigateToPoll(ResultsView.class, id);
        var screen = textOf(currentView());

        assertThat(presenter().poll(id).orElseThrow().awaiting()).containsExactly(Sample.ADA);
        assertThat(screen).doesNotContain("Send a nudge?").contains("You haven't picked your days yet.");
    }

    @Test
    @DisplayName("finds somebody from three characters, and nobody from two")
    void theSearchScreenNeedsThreeCharacters() {
        openSearchFor("Roadmap workshop");

        searchFor("sa");
        assertThat(joinedTextOf(currentView())).doesNotContain("sara.naslund@acme.com");

        searchFor("sar");
        assertThat(joinedTextOf(currentView())).contains("sara.naslund@acme.com", "t.sarkar@acme.com");
    }

    @Test
    @DisplayName("adds a match as a chip and stops offering it")
    void addingAMatch() {
        openSearchFor("Roadmap workshop");
        searchFor("sara.nas");

        clickMatchRow();

        assertThat(presenter().draft().invitees()).extracting(Person::email)
                .containsExactly("sara.naslund@acme.com");

        searchFor("sara.nas");
        assertThat(textOf(currentView())).doesNotContain("Matches");
    }

    @Test
    @DisplayName("offers an address with no account behind it as an invitation")
    void invitingAnOutsider() {
        openSearchFor("Roadmap workshop");

        searchFor("lena.ohlsson@studiofern.se");

        assertThat(textOf(currentView())).contains("Invite lena.ohlsson@studiofern.se")
                .contains("Nothing found. We'll email a voting link instead.");

        click("Invite lena.ohlsson@studiofern.se");

        assertThat(presenter().draft().invitees()).singleElement().satisfies(invitee -> {
            assertThat(invitee.email()).isEqualTo("lena.ohlsson@studiofern.se");
            assertThat(invitee.hasAccount()).isFalse();
        });
    }

    @Test
    @DisplayName("splits a pasted list into chips and keeps the one that is not an address")
    void pastingAList() {
        openSearchFor("Roadmap workshop");

        searchFor("tom.beck@acme.com, priya.rao@acme.com\njonas@acme");

        assertThat(presenter().draft().invitees()).extracting(Person::email)
                .containsExactly("tom.beck@acme.com", "priya.rao@acme.com");
        assertThat(queryField().getValue()).isEqualTo("jonas@acme");
    }

    @Test
    @DisplayName("says \"that's you\" rather than pretending nobody answers to your address")
    void searchingForYourself() {
        StubIdentity.signIn(Sample.TOM);
        openSearchFor("Roadmap workshop");

        searchFor("tom");

        assertThat(queryField().isInvalid()).isTrue();
        assertThat(queryField().getErrorMessage()).contains("That's you");
    }

    @Test
    @DisplayName("says so rather than adding somebody twice")
    void refusingADuplicate() {
        openSearchFor("Roadmap workshop");
        searchFor("sara.naslund@acme.com");
        click("Invite sara.naslund@acme.com");

        searchFor("sara.naslund@acme.com");

        assertThat(queryField().isInvalid()).isTrue();
        assertThat(queryField().getErrorMessage()).contains("Sara");
        assertThat(presenter().draft().invitees()).hasSize(1);
    }

    @Test
    @DisplayName("will not leave the search with nobody on the poll")
    void cannotLeaveEmpty() {
        openSearchFor("Roadmap workshop");

        assertThat(buttonLabelled(translation("create.next")).isEnabled()).isFalse();
    }

    @Test
    @DisplayName("draws whole weeks, and only as many as the month needs")
    void theCalendarGridIsAlwaysWholeWeeks() {
        var id = draftPoll("Roadmap workshop");
        navigateToPoll(CandidateDaysView.class, id);
        var calendar = calendarField();

        for (var month : List.of(YearMonth.of(2026, 8), YearMonth.of(2026, 11),
                YearMonth.of(2026, 12), YearMonth.of(2027, 2), YearMonth.of(2027, 3))) {
            calendar.setValue(Set.of(month.atDay(weekdayIn(month))));
            var cells = componentsOf(calendar)
                    .filter(NativeButton.class::isInstance)
                    .filter(cell -> ((NativeButton) cell).getClassNames().contains("calendar-day"))
                    .count();

            assertThat(cells).as("%s renders %d cells, which is not whole weeks", month, cells)
                    .isIn(28L, 35L, 42L);
        }
    }

    private int weekdayIn(YearMonth month) {
        return month.atDay(1).getDayOfWeek().getValue() <= 5 ? 1 : 3;
    }

    @Test
    @DisplayName("choosing days on the calendar and sending opens the poll")
    void choosingDaysAndSending() {
        var id = draftPoll("Roadmap workshop");
        navigateToPoll(CandidateDaysView.class, id);
        var monday = Sample.mondayAfterNext(presenter().today());

        calendarField().setValue(Set.of(monday));
        click("Send to the team");

        assertThat(currentView()).isInstanceOf(ShareView.class);
        assertThat(presenter().poll(id).orElseThrow().candidateDays()).containsExactly(monday);

        click("Send " + presenter().poll(id).orElseThrow().inviteCount() + " invites");

        assertThat(presenter().poll(id).orElseThrow().state()).isEqualTo(PollState.OPEN);
        assertThat(currentView()).isInstanceOf(ResultsView.class);
    }

    @Test
    @DisplayName("refuses to send a poll with nothing on the table")
    void sendingAnEmptyPoll() {
        var id = draftPoll("Roadmap workshop");
        navigateToPoll(CandidateDaysView.class, id);

        click("Send to the team");

        assertThat(currentView()).isInstanceOf(CandidateDaysView.class);
        assertThat(presenter().poll(id).orElseThrow().candidateDays()).isEmpty();
    }

    @Test
    @DisplayName("proposes a day from an inline calendar, never from an overlay")
    void proposingADayInline() {
        StubIdentity.signIn(Sample.JONAS);
        navigateToPoll(NoDayWorksView.class, offsite);
        var proposal = presenter().today().plusWeeks(6).with(DayOfWeek.TUESDAY);

        proposalCalendar().setValue(Set.of(proposal));

        assertThat(componentsOf(currentView()).filter(DayPoster.class::isInstance)).hasSize(1);

        click("Send my answer");

        assertThat(presenter().ballotOf(offsite)).get().satisfies(ballot -> {
            assertThat(ballot.isDeclined()).isTrue();
            assertThat(ballot.proposedDays()).containsExactly(proposal);
        });
    }

    @Test
    @DisplayName("stops offering days once three are proposed, and folds itself away")
    void proposalsAreCappedAtThree() {
        StubIdentity.signIn(Sample.JONAS);
        navigateToPoll(NoDayWorksView.class, offsite);
        var calendar = proposalCalendar();
        var monday = presenter().today().plusWeeks(6).with(DayOfWeek.MONDAY);

        calendar.setValue(Set.of(monday, monday.plusDays(1), monday.plusDays(2)));

        assertThat(calendar.isAtMaximumSelection()).isTrue();
        assertThat(componentsOf(currentView()).filter(DayPoster.class::isInstance)).hasSize(3);
        assertThat(enabledCalendarCells(calendar)).isEqualTo(3);
        assertThat(componentsOf(currentView())
                .filter(NativeButton.class::isInstance)
                .map(NativeButton.class::cast)
                .filter(button -> button.getClassNames().contains("poster-add"))).isEmpty();
    }

    @Test
    @DisplayName("lets a proposed day be swapped for another without an error")
    void aProposalCanBeSwapped() {
        StubIdentity.signIn(Sample.JONAS);
        navigateToPoll(NoDayWorksView.class, offsite);
        var calendar = proposalCalendar();
        var monday = presenter().today().plusWeeks(6).with(DayOfWeek.MONDAY);
        calendar.setValue(Set.of(monday, monday.plusDays(1), monday.plusDays(2)));

        calendar.setValue(Set.of(monday, monday.plusDays(1)));

        assertThat(calendar.isAtMaximumSelection()).isFalse();
        assertThat(enabledCalendarCells(calendar)).isGreaterThan(3);
    }

    @Test
    @DisplayName("will not let a candidate day be proposed as an alternative to itself")
    void cannotProposeADayAlreadyOnTheTable() {
        StubIdentity.signIn(Sample.JONAS);
        navigateToPoll(NoDayWorksView.class, offsite);
        var onTheTable = presenter().poll(offsite).orElseThrow().candidateDays();

        var offered = componentsOf(proposalCalendar())
                .filter(NativeButton.class::isInstance)
                .map(NativeButton.class::cast)
                .filter(cell -> cell.getClassNames().contains("calendar-day"))
                .filter(NativeButton::isEnabled)
                .map(NativeButton::getText)
                .toList();

        assertThat(offered).isNotEmpty();
        onTheTable.forEach(day ->
                assertThat(offered).doesNotContain(String.valueOf(day.getDayOfMonth())));
    }

    @Test
    @DisplayName("offers no calendar when the organizer is not taking other days")
    void alternativesCanBeTurnedOff() {
        presenter().allowAlternatives(offsite, false);
        StubIdentity.signIn(Sample.JONAS);

        navigateToPoll(NoDayWorksView.class, offsite);

        assertThat(componentsOf(currentView()).filter(MonthCalendar.class::isInstance)).isEmpty();
        assertThat(textOf(currentView()))
                .contains("I can't make any of these", "isn't taking other days")
                .doesNotContain("Days I could do instead");

        click("Send my answer");

        assertThat(presenter().ballotOf(offsite)).get().satisfies(ballot -> {
            assertThat(ballot.isDeclined()).isTrue();
            assertThat(ballot.proposedDays()).isEmpty();
        });
    }

    @Test
    @DisplayName("lets the organizer turn other days off from the days screen")
    void theOrganizerTurnsAlternativesOff() {
        navigateToPoll(CandidateDaysView.class, offsite);

        var allowed = componentsOf(currentView())
                .filter(Checkbox.class::isInstance)
                .map(Checkbox.class::cast)
                .findFirst()
                .orElseThrow();

        assertThat(allowed.getValue()).isTrue();
        allowed.setValue(false);

        assertThat(presenter().poll(offsite).orElseThrow().alternativesAllowed()).isFalse();
    }

    @Test
    @DisplayName("a decline reaches the organizer as a proposal they can accept")
    void decliningReachesTheOrganizer() {
        var proposed = presenter().today().plusWeeks(6).with(DayOfWeek.TUESDAY);
        StubIdentity.signIn(Sample.JONAS);
        presenter().declineAll(offsite, List.of(proposed), "Away that week");
        StubIdentity.signIn(Sample.ADA);

        navigateToPoll(ResultsView.class, offsite);

        assertThat(textOf(currentView())).contains("Proposed instead", "Jonas");

        click("Add it");

        assertThat(presenter().poll(offsite).orElseThrow().candidateDays()).contains(proposed);
    }

    @Test
    @DisplayName("locking from the results screen hands over to the locked date")
    void lockingFromTheResults() {
        navigateToPoll(ResultsView.class, offsite);
        var leader = presenter().poll(offsite).orElseThrow().leader().orElseThrow().day();

        clickStartingWith("Lock in");

        assertThat(presenter().poll(offsite).orElseThrow().lockedDay()).isEqualTo(leader);
        assertThat(currentView()).isInstanceOf(LockedView.class);
    }

    @Test
    @DisplayName("sends a settled poll's results screen straight to the locked date")
    void aSettledPollHasNoStandings() {
        var leader = presenter().poll(offsite).orElseThrow().leader().orElseThrow().day();
        presenter().lock(offsite, leader);

        navigateToPoll(ResultsView.class, offsite);

        assertThat(currentView()).isInstanceOf(LockedView.class);
    }

    @Test
    @DisplayName("turns the whole screen over to a settled date")
    void locked() {
        var leader = presenter().poll(offsite).orElseThrow().leader().orElseThrow().day();
        presenter().lock(offsite, leader);

        navigateToPoll(LockedView.class, offsite);

        assertThat(textOf(currentView()))
                .contains("Date locked", String.valueOf(leader.getDayOfMonth()));
    }

    @Test
    @DisplayName("shows a poll nobody has answered as an empty grid, not as an error")
    void unansweredPoll() {
        var id = Sample.unanswered(context.getBean(PollService.class), clock());

        navigateToPoll(ResultsView.class, id);

        assertThat(textOf(currentView())).contains("0 of 7", "Waiting on");
    }

    @Test
    @DisplayName("sends a link nobody recognises to the not-found screen")
    void unknownPoll() {
        navigateToPoll(ResultsView.class, UUID.randomUUID());

        assertThat(currentView()).isInstanceOf(NotFoundView.class);
    }

    @Test
    @DisplayName("sends a link that is not even an id to the not-found screen")
    void malformedPollId() {
        UI.getCurrent().navigate(ResultsView.class, new RouteParameters("id", "no-such-poll"));

        assertThat(currentView()).isInstanceOf(NotFoundView.class);
    }

    @Test
    @DisplayName("carries a new poll from its name through to the share screen")
    void createAndShare() {
        var id = draftPoll("Roadmap workshop");
        presenter().chooseDays(id, Set.of(Sample.mondayAfterNext(presenter().today())));

        navigateToPoll(ShareView.class, id);

        assertThat(textOf(currentView())).contains("Voting link", "Invited", "vote/" + id);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("everyScreen")
    @DisplayName("every screen has a way home, and it goes home")
    void everyScreenCanLeave(String ignoredName, Class<? extends Component> view, boolean needsSlug) {
        if (needsSlug) {
            navigateToPoll(view, offsite);
        } else {
            UI.getCurrent().navigate(view);
        }
        assertThat(currentView()).isInstanceOf(view);

        clickHome();

        assertThat(currentView()).isInstanceOf(PollsView.class);
    }

    private static Stream<Arguments> everyScreen() {
        return Stream.of(
                Arguments.of("create", NewPollView.class, false),
                Arguments.of("candidate days", CandidateDaysView.class, true),
                Arguments.of("share", ShareView.class, true),
                Arguments.of("none of these work", NoDayWorksView.class, true),
                Arguments.of("receipt", ReceiptView.class, true),
                Arguments.of("results", ResultsView.class, true),
                Arguments.of("not found", NotFoundView.class, false));
    }

    @Test
    @DisplayName("the locked screen can leave too, once a poll is settled")
    void theLockedScreenCanLeave() {
        var leader = presenter().poll(offsite).orElseThrow().leader().orElseThrow().day();
        presenter().lock(offsite, leader);
        navigateToPoll(LockedView.class, offsite);

        clickHome();

        assertThat(currentView()).isInstanceOf(PollsView.class);
    }

    // ---- Building the polls a test needs ----

    private UUID draftPoll(String title) {
        presenter().draft().reset();
        presenter().draft().rename(title);
        presenter().draft().invite(Sample.MIRO);
        return presenter().createFromDraft();
    }

    private UUID openPoll(String title) {
        var id = draftPoll(title);
        presenter().chooseDays(id, Set.of(Sample.mondayAfterNext(presenter().today())));
        presenter().send(id);
        return id;
    }

    // ---- Reaching into the screen ----

    private long enabledCalendarCells(MonthCalendar calendar) {
        return componentsOf(calendar)
                .filter(NativeButton.class::isInstance)
                .map(NativeButton.class::cast)
                .filter(cell -> cell.getClassNames().contains("calendar-day"))
                .filter(NativeButton::isEnabled)
                .count();
    }

    private List<NativeButton> dayRows() {
        return componentsOf(currentView())
                .filter(NativeButton.class::isInstance)
                .map(NativeButton.class::cast)
                .filter(row -> row.getClassNames().contains("day-row"))
                .toList();
    }

    /** What the client sends when a native button is pressed. */
    private void tap(NativeButton row) {
        ComponentUtil.fireEvent(row, new ClickEvent<>(row));
    }

    private MonthCalendar proposalCalendar() {
        return componentsOf(currentView())
                .filter(MonthCalendar.class::isInstance)
                .map(MonthCalendar.class::cast)
                .findFirst()
                .orElseThrow();
    }

    private MonthCalendar calendarField() {
        return proposalCalendar();
    }

    private DayBallot ballotField() {
        return componentsOf(currentView())
                .filter(DayBallot.class::isInstance)
                .map(DayBallot.class::cast)
                .findFirst()
                .orElseThrow();
    }

    private TextField queryField() {
        return componentsOf(currentView())
                .filter(TextField.class::isInstance)
                .map(TextField.class::cast)
                .findFirst()
                .orElseThrow();
    }

    private void openSearchFor(String title) {
        presenter().draft().reset();
        presenter().draft().rename(title);
        UI.getCurrent().navigate(InviteeSearchView.class);
    }

    private void searchFor(String query) {
        queryField().setValue(query);
    }

    private void clickMatchRow() {
        var row = componentsOf(currentView())
                .filter(NativeButton.class::isInstance)
                .map(NativeButton.class::cast)
                .filter(candidate -> candidate.getClassNames().contains("match-row"))
                .findFirst()
                .orElseThrow();
        tap(row);
    }

    /**
     * The way home is an icon on most screens, the wordmark on the two that open with
     * one, and a footer button on the not-found screen — so this keys on the marker
     * they all carry rather than on a shape.
     */
    private void clickHome() {
        var home = componentsOf(currentView())
                .filter(Button.class::isInstance)
                .map(Button.class::cast)
                .filter(candidate -> candidate.getClassNames().contains(Actions.HOME_CLASS))
                .findFirst()
                .orElseThrow(() -> new AssertionError("No way home from "
                        + currentView().getClass().getSimpleName()));
        ComponentUtil.fireEvent(home, new ClickEvent<>(home));
    }

    private String translation(String key) {
        return UI.getCurrent().getTranslation(key);
    }

    private Button buttonLabelled(String label) {
        return componentsOf(currentView())
                .filter(Button.class::isInstance)
                .map(Button.class::cast)
                .filter(button -> label.equals(button.getText()))
                .findFirst()
                .orElseThrow();
    }

    private void click(String label) {
        clickMatching(label, label::equals);
    }

    private void clickStartingWith(String prefix) {
        clickMatching(prefix, text -> text.startsWith(prefix));
    }

    private void clickMatching(String description, java.util.function.Predicate<String> matches) {
        var button = componentsOf(currentView())
                .filter(Button.class::isInstance)
                .map(Button.class::cast)
                .filter(candidate -> candidate.getText() != null && matches.test(candidate.getText()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("No button matching " + description
                        + " on " + currentView().getClass().getSimpleName()));
        ComponentUtil.fireEvent(button, new ClickEvent<>(button));
    }

    private PollPresenter presenter() {
        return context.getBean(PollPresenter.class);
    }

    private void navigateToPoll(Class<? extends Component> view, UUID id) {
        UI.getCurrent().navigate(view, new RouteParameters("id", id.toString()));
    }

    private Component currentView() {
        return (Component) UI.getCurrent().getInternals().getActiveRouterTargetsChain().getFirst();
    }

    private String textOf(Component root) {
        return componentsOf(root)
                .map(this::ownText)
                .filter(text -> !text.isBlank())
                .reduce("", (all, text) -> all + text + "\n");
    }

    /**
     * The same text with nothing between the pieces — the matched run of an address is
     * its own bold span, so an address only reads as one string once they are joined.
     */
    private String joinedTextOf(Component root) {
        return textOf(root).replace("\n", "");
    }

    private String ownText(Component component) {
        var text = component instanceof HasText hasText ? hasText.getText() : "";
        return text == null ? "" : text;
    }

    private Stream<Component> componentsOf(Component root) {
        return Stream.concat(Stream.of(root), root.getChildren().flatMap(this::componentsOf));
    }
}
