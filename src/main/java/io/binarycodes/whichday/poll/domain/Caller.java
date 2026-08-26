package io.binarycodes.whichday.poll.domain;

import java.util.Optional;

import io.binarycodes.whichday.people.domain.Person;

/**
 * Somebody asking to change a poll, and whatever they have to show for it.
 *
 * <p>The writing side of {@code PollService} takes one, and so does the one read with
 * something left to decide. A code widens what may be <em>seen</em> in exactly one case: a
 * draft, which has been shown to nobody, so the link cannot have decided it. Everywhere
 * else the link already did, and the code only widens what may be changed.
 *
 * <p>The code travels with the call rather than being read from the session inside the
 * service: who may do what is decided in the service (see {@code docs/REQUIREMENTS.md}
 * §2), and the service stays free of session state.
 *
 * @param adminCode empty in login mode, and empty in anonymous mode until somebody
 *                  types one on the who-are-you screen
 */
public record Caller(Person person, Optional<String> adminCode) {

    /** Somebody with nothing to show but who they are, which is login mode's only case. */
    public static Caller of(Person person) {
        return new Caller(person, Optional.empty());
    }

    public static Caller of(Person person, Optional<String> adminCode) {
        return new Caller(person, adminCode);
    }
}
