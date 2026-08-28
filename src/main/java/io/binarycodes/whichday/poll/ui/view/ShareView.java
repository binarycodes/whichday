package io.binarycodes.whichday.poll.ui.view;

import jakarta.annotation.security.PermitAll;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.clipboard.Clipboard;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.Route;

import java.util.Set;

import io.binarycodes.whichday.base.ui.Actions;
import io.binarycodes.whichday.base.ui.Counts;
import io.binarycodes.whichday.base.ui.DateText;
import io.binarycodes.whichday.base.ui.HintBar;
import io.binarycodes.whichday.base.ui.Home;
import io.binarycodes.whichday.base.ui.Toast;
import io.binarycodes.whichday.base.ui.TopBar;
import io.binarycodes.whichday.base.ui.Typography;
import io.binarycodes.whichday.people.ui.PersonRow;
import io.binarycodes.whichday.poll.domain.Poll;
import io.binarycodes.whichday.poll.domain.PollState;
import io.binarycodes.whichday.poll.ui.component.MonthCalendar;
import io.binarycodes.whichday.poll.ui.presenter.PollPresenter;
import io.binarycodes.whichday.poll.ui.share.VotingLink;

/**
 * One link, and the list of people who are owed it. Opening the poll for answers is
 * what this screen is for, so it is the last one before the counts start moving — and
 * the one the organizer comes back to when the closing date has to change.
 *
 * <p>Anonymous mode has no list — the link is the invitation, and who follows it is
 * not known until they answer — so what stands in its place is the admin code. It is
 * shown here and nowhere else, because this is the one moment the person who called
 * the poll is certainly looking.
 */
@PermitAll
@Route("poll/:id/share")
public class ShareView extends PollScreen {

    /** Beyond this the list would fill the screen; the rest become one row of names. */
    private static final int NAMED_INVITEES = 4;

    public ShareView(PollPresenter presenter) {
        super(presenter);
    }

    /**
     * Editing a poll is the organizer's, and only while it is still taking answers.
     * Anybody else who follows this URL, and everybody once voting is over, is sent to
     * the screen the poll actually has for them. Not the not-found screen: an invitee
     * may see the poll, just not change it.
     */
    @Override
    protected boolean redirect(BeforeEnterEvent event, Poll poll) {
        if (presenter.isOrganizer(poll) && poll.isEditable()) {
            return false;
        }
        forwardToPoll(event, ResultsView.class);
        return true;
    }

    @Override
    protected void build(Poll poll) {
        body(new TopBar(getTranslation("share.title"))
                .withBack(getTranslation("nav.back"), () -> goTo(CandidateDaysView.class))
                .withHome(Home.labelFor(this, presenter), this::goHome));

        var headline = Typography.displayMedium(presenter.anonymous()
                ? getTranslation("share.headline.anonymous", Counts.days(this, poll.candidateDays().size()))
                : getTranslation("share.headline",
                        Counts.days(this, poll.candidateDays().size()), poll.inviteCount()));
        headline.addClassName("push-2xl");
        body(headline);

        body(linkCard(poll));
        body(presenter.anonymous() ? adminCodeCard() : inviteList(poll));

        footer(closingSection(poll), footerAction(poll));
    }

    /**
     * When voting ends, and the way to change it. The design's own copy promises this
     * — "You can extend it later" — so the note is the affordance: tapping it reveals
     * a calendar inline, bounded by the first day on the table, since an answer that
     * arrives after that is about a day already gone.
     */
    private Div closingSection(Poll poll) {
        var chosen = presenter.plannedClosing(poll.id()).orElse(null);
        var note = new HintBar(VaadinIcon.CLOCK, chosen == null
                ? getTranslation("share.closes.unknown")
                : getTranslation("share.closes", DateText.closing(this, chosen)));

        var section = new Div(note);
        section.addClassName("stack-s");
        if (chosen == null) {
            return section;
        }

        var picker = new Div();
        picker.addClassNames("proposal-picker", "closing-picker");
        picker.setVisible(false);

        var calendar = new MonthCalendar(presenter.today(),
                getTranslation("days.previousMonth"),
                getTranslation("days.nextMonth"));
        calendar.addClassName("calendar-field");
        calendar.setMaximumSelection(1);
        presenter.latestClosingDay(poll.id()).ifPresent(calendar::setLatestSelectable);
        calendar.setValue(Set.of(chosen));
        calendar.addValueChangeListener(event -> event.getValue().stream().findFirst()
                .ifPresent(day -> {
                    presenter.closeOn(poll.id(), day);
                    render();
                }));
        picker.add(calendar);

        note.withAction(Actions.inline(getTranslation("share.closes.change"),
                ignored -> picker.setVisible(!picker.isVisible())));
        section.add(picker);
        return section;
    }

    /**
     * Not the same act in both states. A draft is opened for answers by this button. A
     * poll already out has had each edit written as it was made — the closing date the
     * moment it was picked — so there is nothing left to commit and the button is only
     * the way back to the standings. It says so: a button that navigates does not claim
     * to send.
     *
     * <p>Opening reads the same in both modes, because it is the same act. It used to
     * count the invitees in login mode and offer to send to them, which named something
     * that does not happen: there is no transport, and what the button does is make the
     * poll answerable.
     */
    private Button footerAction(Poll poll) {
        if (poll.state() != PollState.DRAFT) {
            return Actions.primary(getTranslation("share.done"), ignored -> goTo(ResultsView.class));
        }
        return Actions.primary(getTranslation("share.open"), ignored -> openForAnswers());
    }

    /**
     * The six digits, and the warning that goes with them. There is no second copy
     * anywhere — no account to attach the poll to and no list to find it in — so a
     * code nobody wrote down is a poll nobody can change again.
     *
     * <p>The warning names the link as well as the code, because the code alone opens
     * nothing. It is checked against the poll being changed and never looked up across
     * the table ({@code docs/REQUIREMENTS.md} §2), which is exactly what makes six digits
     * safe to hand out — so the way back in is the link first and the code second. Said
     * here because this is the one screen that hands the code over, and somebody who
     * wrote down only the digits has kept the half that cannot be used on its own.
     */
    private Div adminCodeCard() {
        var code = presenter.adminCode(id()).orElse("");
        var label = Typography.meta(getTranslation("share.code"));
        var digits = new Span(code);
        digits.addClassName("admin-code");
        var text = new Div(label, digits);
        text.addClassName("link-text");

        var card = new Div(text, copyCodeButton(code));
        card.addClassNames("link-card", "push-m");

        var warning = new HintBar(VaadinIcon.KEY, getTranslation("share.code.keep"));
        var section = new Div(card, warning);
        section.addClassNames("stack-s", "push-2xl");
        return section;
    }

    /**
     * Six digits read off a screen are six digits somebody can mistype, so the card
     * offers them whole. Written down is still the advice — a clipboard survives one
     * copy and this is the only place the code is shown — which is why the warning
     * beneath says what it says and this button does not replace it.
     *
     * <p>Bound to the click rather than run from a click listener, for the reason
     * {@link VotingLink#shareFrom} sets out at length: the clipboard needs the gesture
     * and a server round trip has already spent it.
     */
    private Button copyCodeButton(String code) {
        var copy = Actions.icon(VaadinIcon.COPY, getTranslation("share.code.copy"));
        copy.addClassName("action-copy");
        Clipboard.onClick(copy).writeText(code,
                copied -> Toast.success(getTranslation("share.code.copied")),
                failure -> Toast.error(getTranslation("share.code.copyFailed")));
        return copy;
    }

    private Div linkCard(Poll poll) {
        var label = Typography.meta(getTranslation("share.link"));
        var url = new Span(VotingLink.display(poll.id()));
        url.addClassName("link-url");
        var text = new Div(label, url);
        text.addClassName("link-text");

        var share = Actions.primary(getTranslation("share.share"));
        share.addClassName("action-share");
        VotingLink.shareFrom(share, poll);

        var card = new Div(text, share);
        card.addClassNames("link-card", "push-xl");
        return card;
    }

    private Div inviteList(Poll poll) {
        var header = new Div(Typography.fieldLabel(getTranslation("share.invited")),
                new Span(String.valueOf(poll.inviteCount())));
        header.addClassNames("row-between", "push-2xl");

        var status = getTranslation(poll.state() == PollState.DRAFT ? "share.notSent" : "share.sent");
        var rows = new Div();
        rows.addClassNames("stack", "push-m");
        poll.invited().stream().limit(NAMED_INVITEES)
                .forEach(person -> rows.add(new PersonRow(person, status)));

        var remaining = poll.invited().stream().skip(NAMED_INVITEES).toList();
        if (!remaining.isEmpty()) {
            var names = remaining.stream().map(person -> person.firstName()).toList();
            var overflow = new Span(getTranslation("count.overflow", remaining.size()));
            overflow.addClassName("avatar-stack-overflow");
            var label = new Span(String.join(", ", names));
            label.addClassNames("person-name", "meta-faint");
            var row = new Div(overflow, label);
            row.addClassName("person-row");
            rows.add(row);
        }
        return new Div(header, rows);
    }

    private void openForAnswers() {
        presenter.send(id());
        Toast.success(getTranslation("share.opened"));
        goTo(ResultsView.class);
    }

    @Override
    public String getPageTitle() {
        return getTranslation("share.title");
    }
}
