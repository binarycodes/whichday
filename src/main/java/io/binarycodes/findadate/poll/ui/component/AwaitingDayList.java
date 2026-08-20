package io.binarycodes.findadate.poll.ui.component;

import java.time.LocalDate;
import java.util.List;

import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Span;

import io.binarycodes.findadate.base.ui.DateText;

/**
 * The candidate days before anybody has answered: the same rows the voting screen
 * draws, with an empty track where the count would go. The grid stays and the paint
 * has not arrived, which is what tells the organizer the poll went out and nothing
 * came back.
 */
public class AwaitingDayList extends Div {

    public AwaitingDayList(List<LocalDate> days) {
        addClassName("stack-s");
        days.forEach(day -> add(rowFor(day)));
    }

    private Div rowFor(LocalDate day) {
        var number = new Div(new Span(DateText.dayNumber(day)));
        number.addClassName("day-row-number");
        var weekday = new Div(new Span(DateText.weekdayAbbreviation(this, day)));
        weekday.addClassName("day-row-weekday");
        var numeral = new Div(number, weekday);
        numeral.addClassName("day-row-numeral");

        var track = new Div();
        track.addClassName("day-row-track");

        var row = new Div(numeral, track);
        row.addClassNames("day-row", "day-row-awaiting");
        return row;
    }
}
