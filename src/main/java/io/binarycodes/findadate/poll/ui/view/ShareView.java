package io.binarycodes.findadate.poll.ui.view;

import com.vaadin.flow.component.html.Anchor;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.router.Route;

import io.binarycodes.findadate.base.ui.Actions;
import io.binarycodes.findadate.base.ui.Counts;
import io.binarycodes.findadate.base.ui.DateText;
import io.binarycodes.findadate.base.ui.HintBar;
import io.binarycodes.findadate.base.ui.TopBar;
import io.binarycodes.findadate.base.ui.Typography;
import io.binarycodes.findadate.people.ui.PersonRow;
import io.binarycodes.findadate.poll.domain.Poll;
import io.binarycodes.findadate.poll.domain.PollState;
import io.binarycodes.findadate.poll.ui.presenter.PollPresenter;
import io.binarycodes.findadate.poll.ui.share.CalendarInvite;
import io.binarycodes.findadate.poll.ui.share.MailLink;
import io.binarycodes.findadate.poll.ui.share.VotingLink;

/**
 * One link, and the list of people who are owed it. Sending the invites is what
 * opens the poll, so this is the last screen before the counts start moving.
 */
@Route("poll/:slug/share")
public class ShareView extends PollScreen {

    /** Beyond this the list would fill the screen; the rest become one row of names. */
    private static final int NAMED_INVITEES = 4;

    public ShareView(PollPresenter presenter) {
        super(presenter);
    }

    @Override
    protected void build(Poll poll) {
        body(new TopBar(getTranslation("share.title"))
                .withBack(getTranslation("nav.back"), () -> goTo(CandidateDaysView.class))
                .withTrailingSpace());

        var headline = Typography.displayMedium(getTranslation("share.headline",
                Counts.days(this, poll.candidateDays().size()), poll.inviteCount()));
        headline.addClassName("push-2xl");
        body(headline);

        body(linkCard(poll), shareActions(poll), inviteList(poll));

        var closes = new HintBar(VaadinIcon.CLOCK, getTranslation("share.closes",
                DateText.closing(this, poll.closesAt() == null ? presenter.now() : poll.closesAt())));
        footer(closes, Actions.primary(poll.inviteCount() == 1
                ? getTranslation("share.send.one")
                : getTranslation("share.send.many", poll.inviteCount()), ignored -> send()));
    }

    private Div linkCard(Poll poll) {
        var label = Typography.meta(getTranslation("share.link"));
        var url = new Span(VotingLink.display(poll.slug()));
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
        VotingLink.copyToClipboard(this, poll.slug());
        Notification.show(getTranslation("share.copied"));
    }

    private void send() {
        presenter.send(slug());
        Notification.show(getTranslation("share.sentAll"));
        goTo(ResultsView.class);
    }

    @Override
    public String getPageTitle() {
        return getTranslation("share.title");
    }
}
