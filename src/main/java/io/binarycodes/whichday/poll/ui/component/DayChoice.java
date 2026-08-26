package io.binarycodes.whichday.poll.ui.component;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.vaadin.flow.component.customfield.CustomField;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.NativeButton;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.icon.VaadinIcon;

import io.binarycodes.whichday.base.ui.DateText;

/**
 * One day out of a few, as the rows the voting screen draws — but picking one clears
 * the last, because this is a choice rather than an answer.
 *
 * <p>{@code CustomField<LocalDate>} so the screen treats "which day" as one value with
 * one listener, the way {@code DayBallot} and {@code MonthCalendar} do.
 */
public class DayChoice extends CustomField<LocalDate> {

    private final Div rows = new Div();
    private final Map<LocalDate, NativeButton> rowsByDay = new HashMap<>();

    private final List<LocalDate> days;
    private LocalDate chosen;

    public DayChoice(List<LocalDate> days) {
        this.days = List.copyOf(days);
        rows.addClassName("stack-s");
        add(rows);
        render();
    }

    @Override
    protected LocalDate generateModelValue() {
        return chosen;
    }

    @Override
    protected void setPresentationValue(LocalDate day) {
        chosen = day;
        render();
    }

    private void render() {
        rows.removeAll();
        rowsByDay.clear();
        days.forEach(day -> rows.add(rowFor(day)));
    }

    private NativeButton rowFor(LocalDate day) {
        var number = new Div(new Span(DateText.dayNumber(day)));
        number.addClassName("day-row-number");
        var weekday = new Div(new Span(DateText.weekdayAbbreviation(this, day)));
        weekday.addClassName("day-row-weekday");
        var numeral = new Div(number, weekday);
        numeral.addClassName("day-row-numeral");

        var date = new Div(new Span(DateText.full(this, day)));
        date.addClassName("day-row-date");
        var main = new Div(date);
        main.addClassName("day-row-main");

        var check = new Span(new Icon(VaadinIcon.CHECK));
        check.addClassName("day-row-check");

        var row = new NativeButton();
        row.addClassName("day-row");
        row.add(numeral, main, check);
        row.setAriaLabel(DateText.full(this, day));
        row.getElement().setAttribute("aria-pressed", String.valueOf(day.equals(chosen)));
        row.addClickListener(ignored -> choose(day));
        rowsByDay.put(day, row);
        return row;
    }

    /**
     * Only the two rows that change are touched. Rebuilding the list would throw away
     * the row that was just pressed, and the caret with it.
     */
    private void choose(LocalDate day) {
        var previous = chosen;
        chosen = day;
        markPressed(previous);
        markPressed(day);
        updateValue();
    }

    private void markPressed(LocalDate day) {
        if (day == null) {
            return;
        }
        var row = rowsByDay.get(day);
        if (row != null) {
            row.getElement().setAttribute("aria-pressed", String.valueOf(day.equals(chosen)));
        }
    }
}
