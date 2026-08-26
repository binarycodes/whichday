package io.binarycodes.whichday.people.domain;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.UUID;

/**
 * The address an anonymous session is known by. There is no provider to name anybody in
 * that mode, so a session mints one of these for itself and every poll, invitation and
 * ballot it writes is keyed on it.
 *
 * <p>One class owns the shape because two things need it: the session that mints one, and
 * the retention sweep that recognises one in the {@code account} table. A suffix known in
 * two places is a suffix that can disagree with itself.
 */
public final class AnonymousAddress {

    /**
     * The domain the minted addresses sit under. Not a domain anybody can receive mail
     * at, and not one anybody could register either — which is the point: an address here
     * identifies a session and promises nothing else.
     */
    public static final String DOMAIN = "@whichday.anonymous";

    private static final DateTimeFormatter MINTED_AT = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss");

    private AnonymousAddress() {
    }

    /**
     * A UUID for uniqueness and the moment for legibility: an address that turns up in a
     * database row or a log line says when the session behind it started, which is the
     * only thing anybody can usefully know about it.
     */
    public static String mintedAt(Clock clock) {
        return UUID.randomUUID() + "-" + MINTED_AT.format(LocalDateTime.now(clock)) + DOMAIN;
    }

    /**
     * Whether an address was minted for a session rather than given by a provider. The
     * two never mix: a real address cannot end in this domain, and a minted one is not an
     * address at all.
     */
    public static boolean isMinted(String email) {
        return email != null && email.toLowerCase(Locale.ROOT).endsWith(DOMAIN);
    }
}
