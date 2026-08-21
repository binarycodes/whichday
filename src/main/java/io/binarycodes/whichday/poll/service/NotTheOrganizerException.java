package io.binarycodes.whichday.poll.service;

import java.util.UUID;

/**
 * Thrown when somebody who is on a poll tries to do something only the person who
 * called it may do — settle it, edit it, discard it.
 *
 * <p>It says so plainly, unlike the refusal an uninvited stranger gets: somebody who
 * can see the poll already knows it exists, so there is nothing left to withhold.
 */
public class NotTheOrganizerException extends IllegalStateException {

    NotTheOrganizerException(UUID id, String email) {
        super("Poll " + id + " is not " + email + "'s to change");
    }
}
