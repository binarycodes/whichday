package io.binarycodes.whichday.base.ui;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.Locale;
import java.util.Optional;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.UI;

/**
 * The date, in each of the shapes the design asks for. Every method takes the
 * component that is about to show the text and resolves the locale through it, so
 * nothing here reaches for a static lookup.
 */
public final class DateText {

    private DateText() {
    }

    /** Just the numeral, which is the design's hero on half the screens. */
    public static String dayNumber(LocalDate day) {
        return String.valueOf(day.getDayOfMonth());
    }

    public static String weekdayAbbreviation(Component owner, LocalDate day) {
        var locale = localeOf(owner);
        return day.getDayOfWeek().getDisplayName(TextStyle.SHORT, locale).toUpperCase(locale);
    }

    public static String weekdayShort(Component owner, LocalDate day) {
        return day.getDayOfWeek().getDisplayName(TextStyle.SHORT, localeOf(owner));
    }

    public static String weekdayFull(Component owner, LocalDate day) {
        return day.getDayOfWeek().getDisplayName(TextStyle.FULL, localeOf(owner));
    }

    public static String monthAbbreviation(Component owner, LocalDate day) {
        var locale = localeOf(owner);
        return day.getMonth().getDisplayName(TextStyle.SHORT, locale).toUpperCase(locale);
    }

    public static String monthFull(Component owner, LocalDate day) {
        return day.getMonth().getDisplayName(TextStyle.FULL, localeOf(owner));
    }

    public static String year(LocalDate day) {
        return String.valueOf(day.getYear());
    }

    /** "Monday, September 14" — the whole date, spelled out. */
    public static String full(Component owner, LocalDate day) {
        return DateTimeFormatter.ofPattern("EEEE, MMMM d", localeOf(owner)).format(day);
    }

    /** "Fri 18 Sep" — the tally rows and the settled list. */
    public static String compact(Component owner, LocalDate day) {
        return DateTimeFormatter.ofPattern("EEE d MMM", localeOf(owner)).format(day);
    }

    /** "18 Aug" — a settled poll's date, without its weekday. */
    public static String dayAndMonth(Component owner, LocalDate day) {
        return DateTimeFormatter.ofPattern("d MMM", localeOf(owner)).format(day);
    }

    /**
     * "Friday 11 September" — the last day an answer counts. A date rather than a
     * moment, because the whole product deals in whole days.
     */
    public static String closing(Component owner, LocalDate day) {
        return DateTimeFormatter.ofPattern("EEEE d MMMM", localeOf(owner)).format(day);
    }

    /** The locale the component will render in, for callers that format their own text. */
    public static Locale localeOf(Component owner) {
        return owner.getUI()
                .or(() -> Optional.ofNullable(UI.getCurrent()))
                .map(UI::getLocale)
                .orElseGet(Locale::getDefault);
    }
}
