package io.binarycodes.findadate.people.domain;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * What counts as an address, and how one is taken apart for searching.
 *
 * <p>Deliberately permissive: the point is to tell "still typing" from "that is an
 * address", not to decide whether a mailbox exists. A pattern strict enough to
 * reject a real address is worse than one that lets a typo through — the invite
 * bounces either way, and only one of the two loses the organizer their work.
 */
public final class EmailAddress {

    private static final Pattern SHAPE = Pattern.compile("^[^\\s@,]+@[^\\s@,]+\\.[^\\s@,]{2,}$");
    private static final Pattern SEPARATORS = Pattern.compile("[,;\\n\\r\\t]+");
    private static final Pattern PARTS = Pattern.compile("[.@_+-]");

    private EmailAddress() {
    }

    public static boolean isWellFormed(String candidate) {
        return candidate != null && SHAPE.matcher(candidate.trim()).matches();
    }

    public static String normalise(String candidate) {
        return candidate.trim().toLowerCase(Locale.ROOT);
    }

    /** Commas, semicolons and newlines split a pasted list into candidates. */
    public static List<String> split(String pasted) {
        return Arrays.stream(SEPARATORS.split(pasted))
                .map(String::trim)
                .filter(part -> !part.isEmpty())
                .toList();
    }

    public static boolean looksPasted(String value) {
        return SEPARATORS.matcher(value).find();
    }

    /**
     * The pieces a query may match the start of — so "sar" finds both
     * {@code sara.naslund@acme.com} and {@code t.sarkar@acme.com}, and a search for
     * "acme" does not quietly return the whole company.
     */
    public static List<String> searchableParts(String email) {
        return Arrays.stream(PARTS.split(localPartOf(email)))
                .filter(part -> !part.isEmpty())
                .toList();
    }

    private static String localPartOf(String email) {
        var at = email.indexOf('@');
        return at < 0 ? email : email.substring(0, at);
    }
}
