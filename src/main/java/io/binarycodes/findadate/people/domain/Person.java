package io.binarycodes.findadate.people.domain;

import java.util.Arrays;
import java.util.Locale;
import java.util.stream.Collectors;

/**
 * Somebody who can organize a poll or answer one. The avatar tone is stored
 * rather than derived from the name: it identifies a person across every screen
 * they appear on, and a hash of the name would move it whenever a name is
 * corrected.
 */
public record Person(String id, String name, int avatarTone) {

    private static final int INITIAL_COUNT = 2;

    public String initials() {
        return Arrays.stream(name.trim().split("\\s+"))
                .filter(part -> !part.isEmpty())
                .limit(INITIAL_COUNT)
                .map(part -> part.substring(0, 1).toUpperCase(Locale.ROOT))
                .collect(Collectors.joining());
    }

    public String firstName() {
        var boundary = name.indexOf(' ');
        return boundary < 0 ? name : name.substring(0, boundary);
    }
}
