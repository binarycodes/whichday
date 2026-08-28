package io.binarycodes.whichday.poll.ui.view;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;

import com.vaadin.browserless.SpringBrowserlessTest;
import com.vaadin.flow.component.ClickEvent;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.ComponentUtil;
import com.vaadin.flow.component.HasText;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.checkbox.Checkbox;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.router.RouteParameters;

import io.binarycodes.whichday.AnonymousWhichdayTest;
import io.binarycodes.whichday.Sample;
import io.binarycodes.whichday.TestClock;
import io.binarycodes.whichday.TestDatabase;
import io.binarycodes.whichday.base.config.AccessMode;
import io.binarycodes.whichday.people.service.AccountDirectory;
import io.binarycodes.whichday.people.ui.AdminCodeField;
import io.binarycodes.whichday.people.ui.PersonAvatar;
import io.binarycodes.whichday.people.ui.presenter.AnonymousViewerSession;
import io.binarycodes.whichday.people.ui.presenter.ViewerSession;
import io.binarycodes.whichday.people.ui.view.IdentityView;
import io.binarycodes.whichday.poll.domain.Poll;
import io.binarycodes.whichday.poll.service.InviteeSearch;
import io.binarycodes.whichday.poll.service.NotTheOrganizerException;
import io.binarycodes.whichday.poll.service.PollService;
import io.binarycodes.whichday.poll.ui.component.DayBallot;
import io.binarycodes.whichday.poll.ui.presenter.PollPresenter;

/**
 * The same journey with no provider behind it: a name typed on the way in, a link that
 * works for somebody who has never been here, and six digits standing where the
 * organizer's account would have stood.
 *
 * <p>Nothing signs anybody in — that is the point — so the who-are-you screen is the
 * fixture. The browserless base class gives one {@code VaadinSession} per method, and
 * an identity belongs to a session: naming the same one twice corrects a name rather
 * than becoming somebody else. So a second visitor is a second session, built by hand
 * with a presenter of its own — which is what {@link #visitor} is.
 */
@AnonymousWhichdayTest
@DisplayName("Walking the poll with nobody signed in")
class AnonymousPollJourneyTest extends SpringBrowserlessTest {

    /** The maximum the product decided for a typed name, pinned so removing it fails. */
    private static final int NAME_MAXIMUM = 20;

    @Autowired
    private ApplicationContext context;

    @Autowired
    private TestClock clock;

    @Autowired
    private TestDatabase database;

    @BeforeEach
    void empty() {
        database.empty();
        clock.reset();
    }

    @Test
    @DisplayName("asks who is asking before it shows anything at all")
    void theFrontDoor() {
        UI.getCurrent().navigate(PollsView.class);

        assertThat(currentView()).isInstanceOf(IdentityView.class);
        assertThat(textOf(currentView())).contains(translation("identity.headline"),
                translation("identity.name"));
        // Nothing about a code: there is no poll on the other side of this screen for one
        // to be checked against, so asking would take an answer and do nothing with it.
        assertThat(textOf(currentView())).doesNotContain(translation("identity.code"),
                translation("identity.code.toggle"));
        assertThat(componentsOf(currentView()).filter(Checkbox.class::isInstance)).isEmpty();
    }

    /** The same screen, reached on the way to a poll: now the code has something to answer. */
    @Test
    @DisplayName("asks for a code only when a poll is what it is standing in front of")
    void theCodeIsAskedForOnTheWayToAPoll() {
        var poll = pollCalledBy(visitor("Ada", ""), "Q3 offsite");

        navigateTo(ResultsView.class, poll);

        assertThat(currentView()).isInstanceOf(IdentityView.class);
        assertThat(codeCheckbox().getLabel()).isEqualTo(translation("identity.code.toggle"));
        // Drawn but not shown, because almost nobody following a link has a code either.
        assertThat(codeGroup().isVisible()).isFalse();
    }

    /**
     * The code is the organizer's way back in, and it is the only thing on this screen that
     * most people should never have to think about — so it waits behind the checkbox. A code
     * that arrives on the clipboard in one piece is spread across the boxes rather than
     * truncated to the first digit, which is the whole reason six boxes are bearable.
     */
    @Test
    @DisplayName("reveals the code boxes when asked, and spreads a pasted code across them")
    void theCodeIsBehindTheCheckbox() {
        var organizer = visitor("Ada", "");
        var poll = pollCalledBy(organizer, "Q3 offsite");
        var code = organizer.adminCode(poll).orElseThrow();
        var day = pollOf(poll, organizer).candidateDays().getFirst();

        navigateTo(ResultsView.class, poll);
        assertThat(currentView()).isInstanceOf(IdentityView.class);

        type(0, "Miro");
        tickTheCodeBox();
        assertThat(codeGroup().isVisible()).isTrue();

        // What a paste looks like from the server's side: the whole code in one box.
        codeBoxes().getLast().setValue(code);
        assertThat(codeBoxes()).extracting(TextField::getValue)
                .containsExactly(code.substring(0, 1), code.substring(1, 2), code.substring(2, 3),
                        code.substring(3, 4), code.substring(4, 5), code.substring(5, 6));

        click("Continue");

        assertThat(currentView()).isInstanceOf(ResultsView.class);
        presenter().lock(poll, day);
        assertThat(pollOf(poll, organizer).lockedDay()).isEqualTo(day);
    }

    /** Typing a digit moves to the next box, so six keystrokes fill it without a tab. */
    @Test
    @DisplayName("takes the code a digit at a time, and refuses a half-typed one")
    void aHalfTypedCodeIsRefused() {
        var poll = pollCalledBy(visitor("Ada", ""), "Q3 offsite");

        navigateTo(ResultsView.class, poll);
        type(0, "Miro");
        tickTheCodeBox();

        codeBoxes().get(0).setValue("4");
        codeBoxes().get(1).setValue("8");
        click("Continue");

        // Still at the front door: four digits short of a code is a typo, not a shorter code.
        assertThat(currentView()).isInstanceOf(IdentityView.class);

        codeBoxes().get(2).setValue("3920");
        click("Continue");

        assertThat(currentView()).isInstanceOf(ResultsView.class);
    }

    /**
     * The link is the whole bargain of this mode, so the detour through the front door
     * has to give it back. A guard that dropped the destination would turn every shared
     * link into a trip to the create screen.
     */
    @Test
    @DisplayName("keeps the shared link across the detour and lands on the ballot")
    void theSharedLinkSurvivesTheDetour() {
        var poll = pollCalledBy(visitor("Ada", ""), "Q3 offsite");

        UI.getCurrent().navigate(BallotView.class, new RouteParameters("id", poll.toString()));
        assertThat(currentView()).isInstanceOf(IdentityView.class);

        type(0, "Miro");
        click("Continue");

        assertThat(currentView()).isInstanceOf(BallotView.class);
        assertThat(textOf(currentView())).contains("Q3 offsite");
    }

    @Test
    @DisplayName("has no polls list: home is where a poll starts")
    void thereIsNoList() {
        identifyAs("Ada", "");

        UI.getCurrent().navigate(PollsView.class);

        assertThat(currentView()).isInstanceOf(NewPollView.class);
    }

    @Test
    @DisplayName("asks for a name and days, and nobody to invite")
    void creatingAsksForNoInvitees() {
        identifyAs("Ada", "");
        UI.getCurrent().navigate(NewPollView.class);

        var screen = textOf(currentView());

        assertThat(screen).contains("Event name", "Anyone with the link can answer");
        assertThat(screen).doesNotContain("Who decides with you");
    }

    @Test
    @DisplayName("shows the admin code on the share screen, and nowhere else")
    void theShareScreenHandsOverTheCode() {
        var poll = pollCalledBy("Ada", "Q3 offsite");

        navigateTo(ShareView.class, poll);
        var screen = textOf(currentView());

        // The whole of the warning, because the half that matters is the link: a code is
        // checked against the poll it came with and finds nothing on its own.
        assertThat(screen).contains("Admin code", translation("share.code.keep"));
        assertThat(screen).doesNotContain("Invited");
        assertThat(presenter().adminCode(poll)).get().asString().matches("\\d{6}");
        // The copy button carries no text, so its label is the only thing to assert on —
        // and the clipboard write itself belongs to the click, in the browser.
        assertThat(componentsOf(currentView()))
                .anySatisfy(candidate -> assertThat(candidate)
                        .isInstanceOf(Button.class)
                        .extracting(button -> ((Button) button).getAriaLabel().orElse(""))
                        .isEqualTo(translation("share.code.copy")));

        navigateTo(ResultsView.class, poll);
        assertThat(textOf(currentView())).doesNotContain("Admin code");
    }

    /**
     * An anonymous poll starts empty — the organizer included. Membership is having
     * answered, because nothing else could say who is on it, and the service adds each
     * voter as they answer so that every count, stack and tally downstream reads a
     * ballot that would otherwise belong to nobody.
     */
    @Test
    @DisplayName("starts with nobody on it and adds each voter as they answer")
    void answeringJoinsThePoll() {
        var poll = pollCalledBy("Ada", "Q3 offsite");
        var day = onlyDayOf(poll);
        assertThat(pollOf(poll).inviteCount()).isZero();

        visitor("Miro", "").vote(poll, Set.of(day));

        var answered = pollOf(poll);
        assertThat(answered.inviteCount()).isEqualTo(1);
        assertThat(answered.answerCount()).isEqualTo(1);
        assertThat(answered.awaiting()).isEmpty();
        assertThat(answered.ballots()).singleElement()
                .satisfies(ballot -> assertThat(ballot.voter().displayName()).isEqualTo("Miro"));
    }

    /**
     * Nobody can say who has not voted when nobody was asked, so the screen does not
     * guess. "Everyone but Ada" is a claim about an invitee list that does not exist.
     */
    @Test
    @DisplayName("never says who is missing, because nobody knows who was asked")
    void nobodyIsWaitedOn() {
        var poll = pollCalledBy("Ada", "Q3 offsite");
        visitor("Miro", "").vote(poll, Set.of(onlyDayOf(poll)));

        navigateTo(ResultsView.class, poll);
        var screen = textOf(currentView());

        assertThat(screen).contains("Q3 offsite");
        assertThat(screen).doesNotContain("Waiting on", "WAITING ON", "Everyone");
    }

    @Test
    @DisplayName("refuses to settle the poll for somebody without the code, and allows it with")
    void theCodeIsWhatSettlesIt() {
        var poll = pollCalledBy("Ada", "Q3 offsite");
        var code = presenter().adminCode(poll).orElseThrow();
        var day = onlyDayOf(poll);

        var withoutTheCode = visitor("Miro", "");
        withoutTheCode.vote(poll, Set.of(day));
        assertThatThrownBy(() -> withoutTheCode.lock(poll, day))
                .isInstanceOf(NotTheOrganizerException.class);

        var withTheCode = visitor("Miro", code);

        withTheCode.lock(poll, day);
        assertThat(pollOf(poll).lockedDay()).isEqualTo(day);
    }

    /**
     * Six digits are worth nothing on their own. Checking the code against the poll the
     * caller already holds the link to is what keeps them worth nothing.
     */
    @Test
    @DisplayName("takes six digits that belong to another poll for nothing")
    void aCodeIsOnlyGoodForItsOwnPoll() {
        var mine = pollCalledBy("Ada", "Q3 offsite");
        var code = presenter().adminCode(mine).orElseThrow();

        var miro = visitor("Miro", "");
        var theirs = pollCalledBy(miro, "Design review");

        var tanvi = visitor("Tanvi", code);
        var day = onlyDayOf(theirs);
        assertThatThrownBy(() -> tanvi.lock(theirs, day))
                .isInstanceOf(NotTheOrganizerException.class);
    }

    /**
     * Retention takes an anonymous poll like any other, and the six digits that let
     * somebody change one are worth nothing once there is nothing left to change. The
     * saved link lands where a link nobody issued lands.
     */
    @Test
    @DisplayName("leaves a swept poll's code and link worth nothing")
    void aSweptPollIsGoneForTheCodeHolderToo() {
        var poll = pollCalledBy("Ada", "Q3 offsite");
        var code = presenter().adminCode(poll).orElseThrow();
        var day = onlyDayOf(poll);

        clock.advanceDays(40);
        assertThat(context.getBean(PollService.class).deleteExpiredPolls()).isEqualTo(1);

        var holder = visitor("Miro", code);
        assertThat(holder.poll(poll)).isEmpty();
        assertThatThrownBy(() -> holder.lock(poll, day)).isInstanceOf(IllegalArgumentException.class);

        navigateTo(ResultsView.class, poll);
        assertThat(currentView()).isInstanceOf(NotFoundView.class);
    }

    /**
     * "You can extend it later" is promised on the share screen, and the calendar that
     * keeps the promise lives there — but an organizer coming back follows the poll's own
     * link and lands on the standings. So the way to the date has to start here.
     */
    @Test
    @DisplayName("offers the organizer the closing date from the screen they come back to")
    void theClosingDateIsReachableLater() {
        var organizer = visitor("Ada", "");
        var poll = pollCalledBy(organizer, "Q3 offsite");
        var code = organizer.adminCode(poll).orElseThrow();

        identifyAs("Miro", "");
        navigateTo(ResultsView.class, poll);
        assertThat(textOf(currentView())).doesNotContain(translation("results.closing.change"));

        identifyAs("Miro", code);
        navigateTo(ResultsView.class, poll);
        assertThat(textOf(currentView())).contains(translation("results.closing.change"));

        click("Change the date");

        assertThat(currentView()).isInstanceOf(ShareView.class);
    }

    /**
     * The standings header stacked avatars, and an initial identifies nobody here for
     * the same reason it identifies nobody on the ballot.
     */
    @Test
    @DisplayName("names the people who answered on the standings, rather than stacking initials")
    void theStandingsNameWhoAnswered() {
        var poll = pollCalledBy("Ada", "Q3 offsite");
        visitor("Rob Nieminen", "").vote(poll, Set.of(onlyDayOf(poll)));

        navigateTo(ResultsView.class, poll);

        assertThat(textOf(currentView())).contains("Rob");
        assertThat(componentsOf(currentView()).filter(PersonAvatar.class::isInstance)).isEmpty();
    }

    /**
     * The last screen, and the one anybody keeps — so it is the worst place of the three
     * to show a letter that identifies nobody.
     */
    @Test
    @DisplayName("names who is coming on the locked screen, rather than stacking initials")
    void theLockedScreenNamesWhoIsComing() {
        var poll = pollCalledBy("Ada", "Q3 offsite");
        var day = onlyDayOf(poll);
        visitor("Rob Nieminen", "").vote(poll, Set.of(day));
        presenter().lock(poll, day);

        navigateTo(LockedView.class, poll);

        assertThat(textOf(currentView())).contains("Rob");
        assertThat(componentsOf(currentView()).filter(PersonAvatar.class::isInstance)).isEmpty();
    }

    /**
     * Every one of these promises a message, and there is nobody to send one to: no
     * addresses, no invitee list, no account. Offering them would be the screen writing
     * a cheque the deployment cannot cash. Login mode has the addresses and still no
     * transport, so it goes without them too — {@code PollJourneyTest} guards that half.
     */
    @Test
    @DisplayName("offers no reminder, no nudge and nobody to tell")
    void nothingPromisesAMessage() {
        var poll = pollCalledBy("Ada", "Q3 offsite");
        var day = onlyDayOf(poll);

        navigateTo(ResultsView.class, poll);
        assertThat(textOf(currentView())).doesNotContain("reminder", "nudge", "Nudge");

        visitor("Miro", "").vote(poll, Set.of(day));
        navigateTo(ResultsView.class, poll);
        assertThat(textOf(currentView())).doesNotContain("reminder", "nudge", "Nudge");

        presenter().lock(poll, day);
        navigateTo(LockedView.class, poll);
        var locked = textOf(currentView());
        assertThat(locked).contains("Add to calendar");
        assertThat(locked).doesNotContain("Tell the team");
    }

    /**
     * An avatar is initials, and a name typed minutes ago makes initials meaningless —
     * two people who both called themselves something with an R are the same letter.
     * So the ballot names its voters here and shows faces in login mode, which
     * {@code PollJourneyTest.theBallotShowsFaces} is the other half of.
     */
    @Test
    @DisplayName("names the people who voted for a day rather than showing initials")
    void theBallotNamesItsVoters() {
        var poll = pollCalledBy("Ada", "Q3 offsite");
        var day = onlyDayOf(poll);
        visitor("Rob Nieminen", "").vote(poll, Set.of(day));

        navigateTo(BallotView.class, poll);

        // The field, not the screen: the header carries the viewer's own avatar either way.
        assertThat(textOf(ballotField())).contains("Rob");
        assertThat(componentsOf(ballotField()).filter(PersonAvatar.class::isInstance)).isEmpty();
    }

    /** Past six the tail is a count, so a popular day cannot push the row off the screen. */
    @Test
    @DisplayName("names six people and counts the rest")
    void theBallotCountsTheTail() {
        var poll = pollCalledBy("Ada", "Q3 offsite");
        var day = onlyDayOf(poll);
        List.of("Rob", "Ab", "Ajaj", "Akekek", "Djdjd", "Ekeke", "Djj", "Nils")
                .forEach(name -> visitor(name, "").vote(poll, Set.of(day)));

        navigateTo(BallotView.class, poll);
        var field = textOf(ballotField());

        assertThat(field).contains("Rob", "Ab", "Ajaj", "Akekek", "Djdjd", "Ekeke");
        assertThat(field).doesNotContain("Djj", "Nils");
        assertThat(field).contains("+2 more");
    }

    /**
     * The link is the credential, so a visitor who was never invited still reads the
     * poll. In login mode this same call answers empty, and that difference is the
     * whole of what anonymous mode trades away.
     */
    @Test
    @DisplayName("shows the poll to somebody who was never put on it")
    void theLinkIsTheCredential() {
        var poll = pollCalledBy("Ada", "Q3 offsite");

        assertThat(visitor("Tanvi", "").poll(poll)).isPresent();
    }

    // ---- Building what a test needs ----

    /** Names this browser's session, the way the who-are-you screen does. */
    private void identifyAs(String name, String adminCode) {
        context.getBean(ViewerSession.class).identify(name, adminCode);
    }

    /**
     * Somebody else entirely: their own session, their own minted address, their own
     * presenter. Hand-built because an identity belongs to a Vaadin session and the
     * test only has one of those.
     */
    private PollPresenter visitor(String name, String adminCode) {
        var session = new AnonymousViewerSession(clock, context.getBean(AccountDirectory.class));
        session.identify(name, adminCode);
        return new PollPresenter(context.getBean(PollService.class),
                context.getBean(InviteeSearch.class), session, clock, AccessMode.ANONYMOUS);
    }

    /** A sent poll with one day on the table, called by somebody with that name. */
    private UUID pollCalledBy(String organizer, String title) {
        identifyAs(organizer, "");
        return pollCalledBy(presenter(), title);
    }

    private UUID pollCalledBy(PollPresenter organizer, String title) {
        organizer.draft().reset();
        organizer.draft().rename(title);
        var id = organizer.createFromDraft();
        organizer.chooseDays(id, Set.of(Sample.mondayAfterNext(organizer.today())));
        organizer.send(id);
        return id;
    }

    private LocalDate onlyDayOf(UUID id) {
        return pollOf(id).candidateDays().getFirst();
    }

    private Poll pollOf(UUID id) {
        return presenter().poll(id).orElseThrow();
    }

    private Poll pollOf(UUID id, PollPresenter reader) {
        return reader.poll(id).orElseThrow();
    }

    // ---- Reaching into the screen ----

    /**
     * The who-are-you screen's plain fields in the order it draws them, which now means the
     * name and then the code's six boxes — {@link #codeBoxes} is the way to those.
     */
    private void type(int index, String value) {
        componentsOf(currentView())
                .filter(TextField.class::isInstance)
                .map(TextField.class::cast)
                .toList()
                .get(index)
                .setValue(value);
    }

    private void click(String label) {
        var button = componentsOf(currentView())
                .filter(Button.class::isInstance)
                .map(Button.class::cast)
                .filter(candidate -> label.equals(candidate.getText()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("No button labelled " + label + " on "
                        + currentView().getClass().getSimpleName()));
        ComponentUtil.fireEvent(button, new ClickEvent<>(button));
    }

    /**
     * The name is the one field this mode adds, and twenty characters is what it takes: a
     * first name and a last one, read on a ballot line and beside an avatar.
     */
    @Test
    @DisplayName("caps the name at what a name needs")
    void theNameHasAMaximum() {
        UI.getCurrent().navigate(PollsView.class);

        var name = componentsOf(currentView())
                .filter(TextField.class::isInstance)
                .map(TextField.class::cast)
                .findFirst()
                .orElseThrow();

        assertThat(name.getMaxLength()).isEqualTo(NAME_MAXIMUM);
    }

    /** The checkbox that says the visitor called a poll and wants to change it. */
    private Checkbox codeCheckbox() {
        return componentsOf(currentView())
                .filter(Checkbox.class::isInstance)
                .map(Checkbox.class::cast)
                .findFirst()
                .orElseThrow(() -> new AssertionError("No checkbox on the who-are-you screen"));
    }

    private void tickTheCodeBox() {
        codeCheckbox().setValue(true);
    }

    /** The block the code sits in, label and hint included — what the checkbox reveals. */
    private Component codeGroup() {
        return componentsOf(currentView())
                .filter(candidate -> candidate.getChildren().anyMatch(AdminCodeField.class::isInstance))
                .findFirst()
                .orElseThrow(() -> new AssertionError("No code block on the who-are-you screen"));
    }

    private List<TextField> codeBoxes() {
        return componentsOf(currentView())
                .filter(AdminCodeField.class::isInstance)
                .flatMap(Component::getChildren)
                .filter(TextField.class::isInstance)
                .map(TextField.class::cast)
                .toList();
    }

    private void navigateTo(Class<? extends Component> view, UUID id) {
        UI.getCurrent().navigate(view, new RouteParameters("id", id.toString()));
    }

    private PollPresenter presenter() {
        return context.getBean(PollPresenter.class);
    }

    private String translation(String key, Object... arguments) {
        return UI.getCurrent().getTranslation(key, arguments);
    }

    private DayBallot ballotField() {
        return componentsOf(currentView())
                .filter(DayBallot.class::isInstance)
                .map(DayBallot.class::cast)
                .findFirst()
                .orElseThrow(() -> new AssertionError("No ballot field on "
                        + currentView().getClass().getSimpleName()));
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

    private String ownText(Component component) {
        var text = component instanceof HasText hasText ? hasText.getText() : "";
        return text == null ? "" : text;
    }

    private Stream<Component> componentsOf(Component root) {
        return Stream.concat(Stream.of(root), root.getChildren().flatMap(this::componentsOf));
    }
}
