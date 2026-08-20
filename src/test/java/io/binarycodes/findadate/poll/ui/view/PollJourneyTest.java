package io.binarycodes.findadate.poll.ui.view;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

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
import com.vaadin.flow.component.html.NativeButton;
import com.vaadin.flow.router.RouteParameters;

import io.binarycodes.findadate.Application;
import io.binarycodes.findadate.base.ui.Actions;
import io.binarycodes.findadate.poll.domain.PollState;
import io.binarycodes.findadate.poll.ui.component.DayBallot;
import io.binarycodes.findadate.poll.ui.component.MonthCalendar;
import io.binarycodes.findadate.poll.ui.presenter.PollPresenter;

/**
 * The whole journey without a browser: every screen builds, and the ones that
 * change state hand the next one something to show.
 */
@SpringBootTest(classes = Application.class)
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
@DisplayName("Walking the poll")
class PollJourneyTest extends SpringBrowserlessTest {

    private static final String OFFSITE = "q3-team-offsite";

    @Autowired
    private ApplicationContext context;

    @Test
    @DisplayName("opens on the poll list, with the offsite and the settled ones")
    void pollList() {
        UI.getCurrent().navigate(PollsView.class);

        assertThat(textOf(currentView())).contains("Q3 team offsite", "Design review week", "Settled");
    }

    @Test
    @DisplayName("shows the organizer the standings and the holdout")
    void results() {
        navigateToPoll(ResultsView.class, OFFSITE);

        assertThat(textOf(currentView())).contains("6 of 7", "have voted", "Everyone but Jonas", "Jonas");
    }

    @Test
    @DisplayName("offers a voter every day on the table")
    void ballot() {
        presenter().switchViewer(presenter().poll(OFFSITE).orElseThrow().awaiting().getFirst());
        navigateToPoll(BallotView.class, OFFSITE);

        var rows = componentsOf(currentView()).filter(NativeButton.class::isInstance).toList();

        assertThat(rows).hasSizeGreaterThanOrEqualTo(5);
        assertThat(textOf(currentView())).contains("Tap every day that works.", "0 of 5 selected");
    }

    @Test
    @DisplayName("reads back the answer a voter already gave")
    void receipt() {
        navigateToPoll(ReceiptView.class, OFFSITE);

        assertThat(textOf(currentView())).contains("Your answer is in", "Where the team stands");
    }

    @Test
    @DisplayName("turns the whole screen over to a settled date")
    void locked() {
        var leader = presenter().poll(OFFSITE).orElseThrow().leader().orElseThrow().day();
        presenter().lock(OFFSITE, leader);

        navigateToPoll(LockedView.class, OFFSITE);

        assertThat(textOf(currentView())).contains("Date locked", String.valueOf(leader.getDayOfMonth()));
    }

    @Test
    @DisplayName("shows a poll nobody has answered as an empty grid, not as an error")
    void unansweredPoll() {
        navigateToPoll(ResultsView.class, "design-review-week");

        assertThat(textOf(currentView())).contains("0 of 7", "Waiting on");
    }

    @Test
    @DisplayName("sends a link nobody recognises to the not-found screen")
    void unknownPoll() {
        navigateToPoll(ResultsView.class, "no-such-poll");

        assertThat(currentView()).isInstanceOf(NotFoundView.class);
    }

    @Test
    @DisplayName("carries a new poll from its name through to the share screen")
    void createAndShare() {
        UI.getCurrent().navigate(NewPollView.class);
        var slug = presenter().create("Roadmap workshop");
        presenter().chooseDays(slug, Set.copyOf(presenter().poll(OFFSITE).orElseThrow()
                .candidateDays().stream().limit(2).toList()));

        navigateToPoll(ShareView.class, slug);

        assertThat(textOf(currentView())).contains("Voting link", "Invited", "vote/" + slug);
    }

    @Test
    @DisplayName("tapping days and submitting records a ballot and lands on the receipt")
    void votingRecordsABallot() {
        var holdout = presenter().poll(OFFSITE).orElseThrow().awaiting().getFirst();
        presenter().switchViewer(holdout);
        navigateToPoll(BallotView.class, OFFSITE);
        var days = presenter().poll(OFFSITE).orElseThrow().candidateDays();

        ballotField().setValue(Set.of(days.getFirst(), days.getLast()));
        click("Submit my days");

        assertThat(presenter().ballotOf(OFFSITE)).get().satisfies(ballot ->
                assertThat(ballot.chosenDays()).containsExactlyInAnyOrder(days.getFirst(), days.getLast()));
        assertThat(currentView()).isInstanceOf(ReceiptView.class);
        assertThat(presenter().poll(OFFSITE).orElseThrow().awaiting()).isEmpty();
    }

    @Test
    @DisplayName("refuses an empty ballot rather than recording one")
    void anEmptyBallotIsNotAnAnswer() {
        presenter().switchViewer(presenter().poll(OFFSITE).orElseThrow().awaiting().getFirst());
        navigateToPoll(BallotView.class, OFFSITE);

        click("Submit my days");

        assertThat(presenter().ballotOf(OFFSITE)).isEmpty();
        assertThat(currentView()).isInstanceOf(BallotView.class);
    }

    @Test
    @DisplayName("choosing days on the calendar and sending opens the poll")
    void choosingDaysAndSending() {
        var slug = presenter().create("Roadmap workshop");
        navigateToPoll(CandidateDaysView.class, slug);
        var monday = presenter().today().plusWeeks(2).with(java.time.DayOfWeek.MONDAY);

        calendarField().setValue(Set.of(monday));
        click("Send to the team");

        assertThat(currentView()).isInstanceOf(ShareView.class);
        assertThat(presenter().poll(slug).orElseThrow().candidateDays()).containsExactly(monday);

        click("Send 7 invites");

        assertThat(presenter().poll(slug).orElseThrow().state()).isEqualTo(PollState.OPEN);
        assertThat(currentView()).isInstanceOf(ResultsView.class);
    }

    @Test
    @DisplayName("refuses to send a poll with nothing on the table")
    void sendingAnEmptyPoll() {
        var slug = presenter().create("Roadmap workshop");
        navigateToPoll(CandidateDaysView.class, slug);

        click("Send to the team");

        assertThat(currentView()).isInstanceOf(CandidateDaysView.class);
        assertThat(presenter().poll(slug).orElseThrow().candidateDays()).isEmpty();
    }

    @Test
    @DisplayName("a decline reaches the organizer as a proposal they can accept")
    void decliningReachesTheOrganizer() {
        var holdout = presenter().poll(OFFSITE).orElseThrow().awaiting().getFirst();
        var proposed = presenter().today().plusWeeks(6).with(java.time.DayOfWeek.TUESDAY);
        presenter().switchViewer(holdout);
        presenter().declineAll(OFFSITE, List.of(proposed), "Away that week");

        presenter().switchViewer(presenter().poll(OFFSITE).orElseThrow().organizer());
        navigateToPoll(ResultsView.class, OFFSITE);

        assertThat(textOf(currentView())).contains("Proposed instead", holdout.firstName());

        click("Add it");

        assertThat(presenter().poll(OFFSITE).orElseThrow().candidateDays()).contains(proposed);
    }

    @Test
    @DisplayName("locking from the results screen hands over to the locked date")
    void lockingFromTheResults() {
        navigateToPoll(ResultsView.class, OFFSITE);
        var leader = presenter().poll(OFFSITE).orElseThrow().leader().orElseThrow().day();

        clickStartingWith("Lock in");

        assertThat(presenter().poll(OFFSITE).orElseThrow().lockedDay()).isEqualTo(leader);
        assertThat(currentView()).isInstanceOf(LockedView.class);
    }

    @Test
    @DisplayName("sends a settled poll's results screen straight to the locked date")
    void aSettledPollHasNoStandings() {
        var leader = presenter().poll(OFFSITE).orElseThrow().leader().orElseThrow().day();
        presenter().lock(OFFSITE, leader);

        navigateToPoll(ResultsView.class, OFFSITE);

        assertThat(currentView()).isInstanceOf(LockedView.class);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("everyScreen")
    @DisplayName("every screen has a way home, and it goes home")
    void everyScreenCanLeave(String ignoredName, Class<? extends Component> view, String slug) {
        if (slug == null) {
            UI.getCurrent().navigate(view);
        } else {
            navigateToPoll(view, slug);
        }
        assertThat(currentView()).isInstanceOf(view);

        clickHome();

        assertThat(currentView()).isInstanceOf(PollsView.class);
    }

    private static Stream<Arguments> everyScreen() {
        return Stream.of(
                Arguments.of("create", NewPollView.class, null),
                Arguments.of("candidate days", CandidateDaysView.class, OFFSITE),
                Arguments.of("share", ShareView.class, OFFSITE),
                Arguments.of("ballot", BallotView.class, OFFSITE),
                Arguments.of("none of these work", NoDayWorksView.class, OFFSITE),
                Arguments.of("receipt", ReceiptView.class, OFFSITE),
                Arguments.of("results", ResultsView.class, OFFSITE),
                Arguments.of("not found", NotFoundView.class, null));
    }

    @Test
    @DisplayName("the locked screen can leave too, once a poll is settled")
    void theLockedScreenCanLeave() {
        var leader = presenter().poll(OFFSITE).orElseThrow().leader().orElseThrow().day();
        presenter().lock(OFFSITE, leader);
        navigateToPoll(LockedView.class, OFFSITE);

        clickHome();

        assertThat(currentView()).isInstanceOf(PollsView.class);
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

    private DayBallot ballotField() {
        return componentsOf(currentView())
                .filter(DayBallot.class::isInstance)
                .map(DayBallot.class::cast)
                .findFirst()
                .orElseThrow();
    }

    private MonthCalendar calendarField() {
        return componentsOf(currentView())
                .filter(MonthCalendar.class::isInstance)
                .map(MonthCalendar.class::cast)
                .findFirst()
                .orElseThrow();
    }

    private void click(String label) {
        clickMatching(label, label::equals);
    }

    private void clickStartingWith(String prefix) {
        clickMatching(prefix, text -> text.startsWith(prefix));
    }

    /** Vaadin's Button has no public click, so the event is fired the way the client would. */
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

    private void navigateToPoll(Class<? extends Component> view, String slug) {
        UI.getCurrent().navigate(view, new RouteParameters("slug", slug));
    }

    private Component currentView() {
        return (Component) UI.getCurrent().getInternals().getActiveRouterTargetsChain().getFirst();
    }

    /** Every string the screen would render, flattened — enough to assert on wiring. */
    private String textOf(Component root) {
        return componentsOf(root)
                .map(this::ownText)
                .filter(text -> !text.isBlank())
                .reduce("", (all, text) -> all + text + "\n");
    }

    private String ownText(Component component) {
        var text = component instanceof HasText hasText ? hasText.getText() : "";
        return text == null ? "" : text;
    }

    private Stream<Component> componentsOf(Component root) {
        return Stream.concat(Stream.of(root),
                root.getChildren().flatMap(this::componentsOf));
    }
}
