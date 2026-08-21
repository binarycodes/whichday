package io.binarycodes.whichday.poll.ui.view;

import jakarta.annotation.security.PermitAll;

import com.vaadin.flow.component.html.Anchor;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.Route;

import java.util.Set;

import io.binarycodes.whichday.base.ui.Actions;
import io.binarycodes.whichday.base.ui.Counts;
import io.binarycodes.whichday.base.ui.DateText;
import io.binarycodes.whichday.base.ui.HintBar;
import io.binarycodes.whichday.base.ui.TopBar;
import io.binarycodes.whichday.base.ui.Typography;
import io.binarycodes.whichday.people.ui.PersonRow;
import io.binarycodes.whichday.poll.domain.Poll;
import io.binarycodes.whichday.poll.domain.PollState;
import io.binarycodes.whichday.poll.ui.component.MonthCalendar;
import io.binarycodes.whichday.poll.ui.presenter.PollPresenter;
import io.binarycodes.whichday.poll.ui.share.CalendarInvite;
import io.binarycodes.whichday.poll.ui.share.MailLink;
import io.binarycodes.whichday.poll.ui.share.VotingLink;

/**
 * One link, and the list of people who are owed it. Sending the invites is what
 * opens the poll, so this is the last screen before the counts start moving.
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
     * Editing a poll is the organizer's, so anybody else who follows this URL is sent
     * to the screen the poll actually has for them. Not the not-found screen: they were
     * invited, so the poll is theirs to see — just not theirs to change.
     */
    @Override
    protected boolean redirect(BeforeEnterEvent event, Poll poll) {
        if (presenter.isOrganizer(poll)) {
            return false;
        }
        forwardToPoll(event, ResultsView.class);
        return true;
    }

    @Override
    protected void build(Poll poll) {
        body(new TopBar(getTranslation("share.title"))
                .withBack(getTranslation("nav.back"), () -> goTo(CandidateDaysView.class))
                .withHome(getTranslation("nav.home"), this::goHome));

        var headline = Typography.displayMedium(getTranslation("share.headline",
                Counts.days(this, poll.candidateDays().size()), poll.inviteCount()));
        headline.addClassName("push-2xl");
        body(headline);

        body(linkCard(poll), shareActions(poll), inviteList(poll));

        footer(closingSection(poll), Actions.primary(poll.inviteCount() == 1
                ? getTranslation("share.send.one")
                : getTranslation("share.send.many", poll.inviteCount()), ignored -> send()));
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

    private Div linkCard(Poll poll) {
        var label = Typography.meta(getTranslation("share.link"));
        var url = new Span(VotingLink.display(poll.id()));
        url.addClassName("link-url");
        var text = new Div(label, url);
        text.addClassName("link-text");

        var copy = Actions.primary(getTranslation("share.copy"), ignored -> copyLink(poll));
        copy.addClassName("action-copy");

        var card = new Div(text, copy);
        card.addClassNames("link-card", "push-xl");
        return card;
    }

    private Div shareActions(Poll poll) {
        var message = new Anchor(MailLink.invitation(this, poll), "");
        message.add(new Icon(VaadinIcon.PAPERPLANE), new Span(getTranslation("share.message")));
        message.addClassNames("action", "action-quiet", "action-anchor");

        var calendar = new Anchor(CalendarInvite.forCandidateDays(poll), "");
        calendar.getElement().setAttribute("download", true);
        calendar.add(new Icon(VaadinIcon.CALENDAR), new Span(getTranslation("share.calendar")));
        calendar.addClassNames("action", "action-quiet", "action-anchor");

        var row = new Div(message, calendar);
        row.addClassNames("action-row", "push-m");
        return row;
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

    private void copyLink(Poll poll) {
        VotingLink.copyToClipboard(this, poll.id());
        Notification.show(getTranslation("share.copied"));
    }

    private void send() {
        presenter.send(id());
        Notification.show(getTranslation("share.sentAll"));
        goTo(ResultsView.class);
    }

    @Override
    public String getPageTitle() {
        return getTranslation("share.title");
    }
}
