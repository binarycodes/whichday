package io.binarycodes.whichday.poll.ui.component;

import java.util.List;
import java.util.Optional;
import java.util.function.Function;

import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Span;

import io.binarycodes.whichday.base.ui.DateText;
import io.binarycodes.whichday.poll.domain.DayTally;

/**
 * Where the team stands. One bar per candidate day, filled to its share of
 * everybody invited — never of the leader, so a poll where nobody agrees still
 * looks like a poll where nobody agrees.
 */
public class TallyList extends Div {

    /** Beyond this the fade is imperceptible, so the tail shares the faintest tint. */
    private static final int FAINTEST_RANK = 5;

    /** Only the leader's bar is dark enough to carry text on it. */
    private static final int READABLE_RANK = 1;

    private final Function<DayTally, Optional<String>> captions;

    public TallyList(List<DayTally> tallies, Function<DayTally, Optional<String>> captions) {
        this.captions = captions;
        addClassNames("tally-list");
        tallies.forEach(tally -> add(rowFor(tally)));
    }

    /** The shorter bars the voter's receipt shows the standings in. */
    public TallyList compact() {
        addClassName("tally-compact");
        return this;
    }

    private Div rowFor(DayTally tally) {
        var day = new Span(DateText.compact(this, tally.day()));
        day.addClassName("tally-day");
        var count = new Span(String.valueOf(tally.voteCount()));
        count.addClassName("tally-count");
        var header = new Div(day, count);
        header.addClassNames("row-between", "row-baseline");

        var fill = new Div();
        fill.addClassNames("tally-fill", "tally-rank-" + Math.min(tally.rank(), FAINTEST_RANK));
        fill.getStyle().set("--tally-fill", Math.round(tally.share() * 100) + "%");

        var bar = new Div(fill);
        bar.addClassName("tally-bar");
        if (tally.rank() <= READABLE_RANK) {
            captions.apply(tally).ifPresent(caption -> {
                var text = new Span(caption);
                text.addClassName("tally-caption");
                bar.add(text);
            });
        }

        var row = new Div(header, bar);
        if (tally.rank() > READABLE_RANK + 1) {
            row.addClassName("tally-trailing");
        }
        return row;
    }
}
