package io.binarycodes.whichday.poll.ui.view;

import jakarta.annotation.security.PermitAll;

import com.vaadin.flow.component.html.Anchor;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.router.Route;

import io.binarycodes.whichday.base.ui.Chip;
import io.binarycodes.whichday.base.ui.DateText;
import io.binarycodes.whichday.people.ui.AvatarStack;
import io.binarycodes.whichday.people.ui.NameChips;
import io.binarycodes.whichday.poll.domain.DayTally;
import io.binarycodes.whichday.poll.domain.Poll;
import io.binarycodes.whichday.poll.ui.presenter.PollPresenter;
import io.binarycodes.whichday.poll.ui.share.CalendarInvite;

/**
 * The date, and nothing else. The whole screen turns accent and the numeral takes
 * over — the design's argument that a settled date should look settled.
 */
@PermitAll
@Route("poll/:id/locked")
public class LockedView extends PollScreen {

    /**
     * How many of the people coming this screen names before the rest become a count.
     * The same six the ballot rows use — there is room here, and this is the one screen
     * anybody keeps.
     */
    private static final int NAMED_ANSWERS = 6;

    public LockedView(PollPresenter presenter) {
        super(presenter);
    }

    @Override
    protected void build(Poll poll) {
        addClassName("locked-screen");
        var day = poll.lockedDay();

        var badge = new Chip(getTranslation("locked.badge"), Chip.Tone.OUTLINE);
        badge.addClassName("locked-badge");
        var header = new Div(badge, homeButton());
        header.addClassName("row-between");
        body(header);

        var weekday = new Div(new Span(DateText.weekdayFull(this, day)));
        weekday.addClassName("locked-weekday");
        var number = new Div(new Span(DateText.dayNumber(day)));
        number.addClassName("locked-number");
        var month = new Div(new Span(DateText.monthFull(this, day) + " " + DateText.year(day)));
        month.addClassName("locked-month");
        var summary = new Div(new Span(presenter.anonymous()
                ? getTranslation("locked.summary.anonymous", poll.title(), yesCount(poll))
                : getTranslation("locked.summary", poll.title(), yesCount(poll), poll.inviteCount())));
        summary.addClassName("locked-summary");
        var date = new Div(weekday, number, month, summary);
        date.addClassName("locked-date");
        body(date);

        body(whoSaidYes(poll));

        var addToCalendar = new Anchor(CalendarInvite.forLockedDay(poll), "");
        addToCalendar.getElement().setAttribute("download", true);
        addToCalendar.add(new Icon(VaadinIcon.CALENDAR), new Span(getTranslation("locked.addToCalendar")));
        addToCalendar.addClassNames("action", "action-primary", "action-anchor");

        footer(addToCalendar);
    }

    /**
     * Who is coming. Faces where an account's initials mean something, names where they
     * do not — a visitor typed theirs minutes before answering, so "W" is as likely to
     * be a stranger as the colleague you expected.
     */
    private Div whoSaidYes(Poll poll) {
        if (presenter.anonymous()) {
            var names = NameChips.of(this, poll.answered(), NAMED_ANSWERS);
            names.addClassNames("locked-names", "push-3xl");
            return names;
        }
        var stack = new AvatarStack().large().show(poll.answered());
        stack.addClassName("push-3xl");
        return stack;
    }

    /** How many said yes to the day that won, not how many answered at all. */
    private int yesCount(Poll poll) {
        return poll.tallies().stream()
                .filter(tally -> tally.day().equals(poll.lockedDay()))
                .mapToInt(DayTally::voteCount)
                .findFirst()
                .orElse(0);
    }

    @Override
    public String getPageTitle() {
        return getTranslation("locked.title");
    }
}
