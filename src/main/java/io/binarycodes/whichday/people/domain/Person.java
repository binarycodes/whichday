package io.binarycodes.whichday.people.domain;

import java.util.Arrays;
import java.util.Locale;
import java.util.stream.Collectors;

/**
 * Somebody who can organize a poll or answer one, identified by their email
 * address — which is the only way anybody is reachable here: there is no directory
 * to browse and no user id to look up.
 *
 * <p>{@code name} is blank for an address with no account behind it. Such a person
 * still gets a poll and a vote; what they do not get is a name until they claim one.
 *
 * @param avatarTone stored rather than derived, so a person keeps their colour
 *                   across every screen even if their name is corrected
 */
public record Person(String email, String name, int avatarTone) {

    private static final int INITIAL_COUNT = 2;
    private static final int TONE_COUNT = 4;

    /**
     * An address nobody has an account for. The tone comes from the address so that
     * the same outsider is the same colour on every screen and after every restart.
     */
    public static Person outsider(String email) {
        return new Person(email, "", Math.abs(email.toLowerCase(Locale.ROOT).hashCode()) % TONE_COUNT);
    }

    public boolean hasAccount() {
        return !name.isBlank();
    }

    /** What a name gives us, or what the address gives us instead. */
    public String displayName() {
        return hasAccount() ? name : email;
    }

    public String initials() {
        var source = hasAccount() ? name : localPart().replace('.', ' ');
        return Arrays.stream(source.trim().split("[\\s.]+"))
                .filter(part -> !part.isEmpty())
                .limit(INITIAL_COUNT)
                .map(part -> part.substring(0, 1).toUpperCase(Locale.ROOT))
                .collect(Collectors.joining());
    }

    public String firstName() {
        if (!hasAccount()) {
            return localPart();
        }
        var boundary = name.indexOf(' ');
        return boundary < 0 ? name : name.substring(0, boundary);
    }

    private String localPart() {
        var at = email.indexOf('@');
        return at < 0 ? email : email.substring(0, at);
    }
}
