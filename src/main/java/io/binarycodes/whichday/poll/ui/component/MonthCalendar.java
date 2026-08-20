package io.binarycodes.whichday.poll.ui.component;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.TextStyle;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Locale;
import java.util.Set;
import java.util.stream.IntStream;

import com.vaadin.flow.component.customfield.CustomField;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.NativeButton;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.VaadinIcon;

import io.binarycodes.whichday.base.ui.Actions;
import io.binarycodes.whichday.base.ui.DateText;
import io.binarycodes.whichday.base.ui.Typography;

/**
 * The month grid the organizer puts days on the table with. The oversized month
 * name and the square, paint-filled cells are the design's hero, so this is built
 * from buttons rather than wrapped around a date picker — a picker resolves to one
 * date and styles its own overlay.
 *
 * <p>A cell is the whole control, so each is a native button with {@code
 * aria-pressed} rather than a checkbox that would need a separate label.
 */
public class MonthCalendar extends CustomField<Set<LocalDate>> {

    private static final int WEEK_LENGTH = 7;
    private static final DayOfWeek FIRST_WEEKDAY = DayOfWeek.MONDAY;
    private static final int NO_LIMIT = Integer.MAX_VALUE;

    private final Div monthName = new Div();
    private final Span year = new Span();
    private final Div weekdays = new Div();
    private final Div grid = new Div();
    private final Set<LocalDate> selection = new LinkedHashSet<>();
    private final Set<LocalDate> unavailable = new LinkedHashSet<>();
    private final Map<LocalDate, NativeButton> cells = new HashMap<>();
    private final LocalDate earliestSelectable;

    private YearMonth visibleMonth;
    private int maximumSelection = NO_LIMIT;
    private LocalDate latestSelectable;

    public MonthCalendar(LocalDate today, String previousLabel, String nextLabel) {
        this.earliestSelectable = today;
        this.visibleMonth = YearMonth.from(today);

        weekdays.addClassName("calendar-weekdays");
        grid.addClassName("calendar-grid");

        var previous = Actions.icon(VaadinIcon.ANGLE_LEFT, previousLabel,
                ignored -> showMonth(visibleMonth.minusMonths(1)));
        var next = Actions.icon(VaadinIcon.ANGLE_RIGHT, nextLabel,
                ignored -> showMonth(visibleMonth.plusMonths(1)));

        year.addClassName("meta");
        var navigation = new Div(previous, next);
        navigation.addClassName("calendar-nav");
        var trailing = new Div(year, navigation);
        trailing.addClassNames("row-between", "calendar-trailing");
        var header = new Div(monthName, trailing);
        header.addClassNames("row-between", "row-end", "calendar-header");

        var layout = new Div(header, weekdays, grid);
        layout.addClassName("calendar");
        add(layout);

        renderWeekdays();
        renderGrid();
    }

    @Override
    protected Set<LocalDate> generateModelValue() {
        return Set.copyOf(selection);
    }

    @Override
    protected void setPresentationValue(Set<LocalDate> days) {
        selection.clear();
        if (days != null) {
            selection.addAll(days);
            days.stream().min(LocalDate::compareTo).ifPresent(first -> visibleMonth = YearMonth.from(first));
        }
        renderGrid();
    }

    private void showMonth(YearMonth month) {
        visibleMonth = month;
        renderGrid();
    }

    private void renderWeekdays() {
        weekdays.removeAll();
        orderedWeekdays().forEach(day ->
                weekdays.add(new Span(day.getDisplayName(TextStyle.SHORT, locale()).substring(0, 2))));
    }

    private void renderGrid() {
        monthName.removeAll();
        monthName.add(Typography.displayLarge(DateText.monthFull(this, visibleMonth.atDay(1))));
        year.setText(DateText.year(visibleMonth.atDay(1)));

        grid.removeAll();
        cells.clear();
        // Whole weeks, and only as many as the month needs. Counting to a fixed six
        // rows leaves a month that starts on a Sunday with one orphan cell on its last
        // row, and a four-week February with a whole spare week of March.
        var week = firstCellOf(visibleMonth);
        do {
            for (var offset = 0; offset < WEEK_LENGTH; offset++) {
                grid.add(cellFor(week.plusDays(offset)));
            }
            week = week.plusWeeks(1);
        } while (!YearMonth.from(week).isAfter(visibleMonth));
        applySelectionLimit();
    }

    private NativeButton cellFor(LocalDate day) {
        var cell = new NativeButton(DateText.dayNumber(day));
        cell.addClassName("calendar-day");
        var inMonth = YearMonth.from(day).equals(visibleMonth);
        if (!inMonth) {
            cell.addClassName("calendar-day-outside");
        }
        if (isSelectable(day) && inMonth) {
            cell.getElement().setAttribute("aria-pressed", String.valueOf(selection.contains(day)));
            cell.setAriaLabel(DateText.full(this, day));
            cell.addClickListener(ignored -> toggle(day));
            cells.put(day, cell);
        } else {
            cell.setEnabled(false);
        }
        return cell;
    }

    /**
     * How many days may be chosen at once. At the limit the days not already chosen
     * stop offering themselves, so the ceiling is visible in the grid rather than
     * announced by a rejection — and a chosen day stays live, so swapping one for
     * another takes two taps and no error.
     */
    public void setMaximumSelection(int maximum) {
        this.maximumSelection = maximum;
        applySelectionLimit();
    }

    public boolean isAtMaximumSelection() {
        return selection.size() >= maximumSelection;
    }

    /**
     * Days this calendar will not offer, however otherwise valid — the candidate days
     * already on the table, when it is being used to propose an alternative to them.
     */
    public void setUnavailable(Collection<LocalDate> days) {
        unavailable.clear();
        unavailable.addAll(days);
        renderGrid();
    }

    /** The last day this calendar will offer, when something bounds it from above. */
    public void setLatestSelectable(LocalDate day) {
        this.latestSelectable = day;
        showMonth(YearMonth.from(day));
    }

    /**
     * Whole days in the future, on a working week — the design offers neither a day
     * already gone nor a weekend. See
     * {@code docs/clarifications/0004-calendar-rules.md}.
     */
    private boolean isSelectable(LocalDate day) {
        return !day.isBefore(earliestSelectable)
                && (latestSelectable == null || !day.isAfter(latestSelectable))
                && !unavailable.contains(day)
                && day.getDayOfWeek() != DayOfWeek.SATURDAY
                && day.getDayOfWeek() != DayOfWeek.SUNDAY;
    }

    /**
     * The paint is driven entirely by aria-pressed, so a tap flips that attribute on
     * the cell rather than rebuilding the month. Rebuilding would discard the button
     * that was just pressed, and with it the caret of anybody selecting days from the
     * keyboard.
     */
    private void toggle(LocalDate day) {
        // A calendar that holds one day is a date field, and a date field replaces
        // rather than making the reader clear the old value first.
        if (maximumSelection == 1 && !selection.contains(day)) {
            var previous = Set.copyOf(selection);
            selection.clear();
            selection.add(day);
            previous.forEach(this::markPressed);
        } else if (!selection.remove(day)) {
            selection.add(day);
        }
        markPressed(day);
        applySelectionLimit();
        updateValue();
    }

    private void markPressed(LocalDate day) {
        var cell = cells.get(day);
        if (cell != null) {
            cell.getElement().setAttribute("aria-pressed", String.valueOf(selection.contains(day)));
        }
    }

    /**
     * Enables and disables in place rather than re-rendering, for the same reason
     * {@link #toggle} does: the grid holds the button the reader just pressed.
     */
    private void applySelectionLimit() {
        if (maximumSelection <= 1) {
            return;
        }
        var full = isAtMaximumSelection();
        cells.forEach((day, cell) -> cell.setEnabled(!full || selection.contains(day)));
    }

    /**
     * The grid starts on Monday, not on whatever the locale calls the first day: that
     * is what puts Saturday and Sunday together at the end, and the design greys them
     * as a pair.
     */
    private LocalDate firstCellOf(YearMonth month) {
        var firstOfMonth = month.atDay(1);
        var offset = (firstOfMonth.getDayOfWeek().getValue() - FIRST_WEEKDAY.getValue() + WEEK_LENGTH) % WEEK_LENGTH;
        return firstOfMonth.minusDays(offset);
    }

    private List<DayOfWeek> orderedWeekdays() {
        return IntStream.range(0, WEEK_LENGTH)
                .mapToObj(FIRST_WEEKDAY::plus)
                .toList();
    }

    private Locale locale() {
        return DateText.localeOf(this);
    }
}
