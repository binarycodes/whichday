package io.binarycodes.findadate.poll.ui.component;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.NativeButton;
import com.vaadin.flow.component.html.Span;

import io.binarycodes.findadate.base.ui.DateText;
import io.binarycodes.findadate.poll.domain.PollSummary;

/**
 * One poll in the list, carrying its own date numeral — the day in front, or a dash
 * while the team has not landed on one. The whole row is the target, and it holds a
 * numeral, a title, a note and a state chip, so it is a native button.
 */
public class PollRow extends NativeButton {

    public PollRow(PollSummary summary, String note, String undecided, Component state, Runnable onOpen) {
        addClassName("poll-row");
        if (summary.hasHeadlineDay()) {
            addClassName("poll-row-leading");
        }
        add(numeralOf(summary, undecided), mainOf(summary, note), state);
        addClickListener(ignored -> onOpen.run());
    }

    private Div numeralOf(PollSummary summary, String undecided) {
        var number = new Div(new Span(summary.hasHeadlineDay()
                ? DateText.dayNumber(summary.headlineDay())
                : undecided));
        number.addClassName("poll-row-number");
        var month = new Div(new Span(monthOf(summary)));
        month.addClassName("poll-row-month");
        var block = new Div(number, month);
        block.addClassName("poll-row-numeral");
        if (!summary.hasHeadlineDay()) {
            block.addClassName("poll-row-undecided");
        }
        return block;
    }

    /**
     * A poll with no leader still names a month — the one its candidate days fall in
     * — so the row is anchored in time even before the team agrees.
     */
    private String monthOf(PollSummary summary) {
        var anchor = summary.hasHeadlineDay() ? summary.headlineDay() : summary.firstCandidateDay();
        return anchor == null ? "" : DateText.monthAbbreviation(this, anchor);
    }

    private Div mainOf(PollSummary summary, String note) {
        var title = new Div(new Span(summary.title()));
        title.addClassName("poll-row-title");
        var subtitle = new Div(new Span(note));
        subtitle.addClassName("poll-row-note");
        var block = new Div(title, subtitle);
        block.addClassName("poll-row-main");
        return block;
    }
}
