package io.binarycodes.findadate.poll.ui.component;

import java.time.LocalDate;

import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Span;

import io.binarycodes.findadate.base.ui.DateText;

/**
 * A date as an object: weekday above, numeral below, accent paint behind. Used
 * wherever the design shows a chosen day rather than asks for one — the voter's
 * receipt, and the days somebody puts forward instead.
 */
public class DayPoster extends Div {

    public DayPoster(LocalDate day) {
        addClassName("poster");
        var weekday = new Span(DateText.weekdayAbbreviation(this, day));
        weekday.addClassName("poster-weekday");
        var number = new Span(DateText.dayNumber(day));
        number.addClassName("poster-number");
        add(weekday, number);
    }

    /** The ink hairline the design puts on a proposal but not on a receipt. */
    public DayPoster outlined() {
        addClassName("poster-outlined");
        return this;
    }

    public DayPoster small() {
        addClassName("poster-s");
        return this;
    }
}
