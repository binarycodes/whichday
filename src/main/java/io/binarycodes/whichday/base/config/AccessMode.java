package io.binarycodes.whichday.base.config;

import java.util.Arrays;
import java.util.Locale;
import java.util.stream.Collectors;

/**
 * Which of the two ways into Whichday a deployment chose, set once by
 * {@code WHICHDAY_ACCESS_MODE} and never again.
 *
 * <p>{@link #LOGIN} is an identity provider and nothing else: an account is how you
 * are known, an address is how you are invited, and no screen is reachable without
 * signing in. {@link #ANONYMOUS} has no provider at all: a session says who it is by
 * typing a name, the link is what lets anybody see a poll and answer it, and a
 * six-digit code is what lets anybody change one.
 *
 * <p>The mode is a bean rather than a scattering of {@code @Value} reads so that the
 * few places which genuinely branch — the security chain, the viewer, three checks in
 * {@code PollService} — all ask the same question of the same object.
 */
public enum AccessMode {

    LOGIN,
    ANONYMOUS;

    private static final String UNKNOWN = """
            WHICHDAY_ACCESS_MODE is "%s", which is not a mode Whichday has. It is one of: %s.

            login     an OIDC provider decides who you are. Set WHICHDAY_OIDC_CLIENT_ID,
                      WHICHDAY_OIDC_CLIENT_SECRET and WHICHDAY_OIDC_ISSUER_URI as well.
            anonymous no provider and no accounts. Nothing else to configure.""";

    public boolean isAnonymous() {
        return this == ANONYMOUS;
    }

    /**
     * The property as written, or a startup failure naming both modes. Spring would
     * refuse an unknown profile silently — it simply reads no file for it — so
     * {@code WHICHDAY_ACCESS_MODE=annonymous} would otherwise boot with neither mode's
     * configuration and fail much further along, on a missing bean.
     */
    public static AccessMode named(String mode) {
        return Arrays.stream(values())
                .filter(candidate -> candidate.name().equalsIgnoreCase(mode))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(UNKNOWN.formatted(mode, listed())));
    }

    private static String listed() {
        return Arrays.stream(values())
                .map(mode -> mode.name().toLowerCase(Locale.ROOT))
                .collect(Collectors.joining(", "));
    }
}
