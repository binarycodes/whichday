package io.binarycodes.whichday.base.config;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Locale;
import java.util.Optional;

/**
 * How long Whichday keeps something: a whole number of days, or {@code never}, which
 * keeps it indefinitely. Whole days, like every other span in this application — a
 * poll has a closing date rather than a closing moment (see {@code
 * docs/REQUIREMENTS.md} §6), and a retention window measured in hours would be the
 * only thing here that was not.
 *
 * <p>One type used twice, so the two windows {@link Retention} carries cannot drift
 * apart in what they accept or in how they refuse it.
 */
public record RetentionWindow(Optional<Integer> days) {

    /** Nothing is ever deleted for having reached this window's age. */
    public static final RetentionWindow NEVER = new RetentionWindow(Optional.empty());

    private static final String OFF = "never";

    private static final String UNREADABLE = """
            %s is "%s", which is not a retention window. It is a whole number of days, \
            or "%s" to keep polls indefinitely.""";

    /**
     * The value as written, or a startup failure quoting both it and the variable it
     * came from. A window nobody can parse has no safe reading: guessing at the
     * operator's intent would either delete data they meant to keep or keep data they
     * meant to have gone.
     *
     * @param variable the environment variable to name in a failure, since the property
     *                 it resolved into is not what the operator typed
     */
    public static RetentionWindow of(String variable, String value) {
        var written = value == null ? "" : value.strip();
        if (written.toLowerCase(Locale.ROOT).equals(OFF)) {
            return NEVER;
        }
        return new RetentionWindow(Optional.of(parsed(variable, written)));
    }

    /**
     * The day an anchor has to fall before for this window to have passed it. Empty
     * when the window is off, which is what leaves the whole rule unrun rather than
     * running it against a cutoff that means nothing.
     */
    public Optional<LocalDate> cutoff(LocalDate today) {
        return days.map(count -> today.minusDays(count));
    }

    /**
     * The same cutoff for a column that holds an instant rather than a date. The start of
     * the cutoff day in the clock's own zone, so that comparing instants against it is
     * the same comparison as comparing the days — one unit for both windows, and both of
     * them whole days.
     */
    public Optional<Instant> cutoff(Clock clock) {
        return cutoff(LocalDate.now(clock)).map(day -> day.atStartOfDay(clock.getZone()).toInstant());
    }

    /** How a window reads in a log line: "5 days", or "never". */
    @Override
    public String toString() {
        return days.map(count -> count + " days").orElse(OFF);
    }

    private static int parsed(String variable, String value) {
        try {
            var days = Integer.parseInt(value);
            if (days < 0) {
                throw unreadable(variable, value);
            }
            return days;
        } catch (NumberFormatException notANumber) {
            throw unreadable(variable, value);
        }
    }

    private static IllegalStateException unreadable(String variable, String value) {
        return new IllegalStateException(UNREADABLE.formatted(variable, value, OFF));
    }
}
