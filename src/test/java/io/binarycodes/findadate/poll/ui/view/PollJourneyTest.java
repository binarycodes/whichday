package io.binarycodes.findadate.poll.ui.view;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;

import com.vaadin.browserless.SpringBrowserlessTest;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.HasText;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.html.NativeButton;
import com.vaadin.flow.router.RouteParameters;

import io.binarycodes.findadate.Application;
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
