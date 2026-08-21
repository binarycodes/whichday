package io.binarycodes.whichday.poll.service;

import java.util.UUID;

import io.binarycodes.whichday.poll.domain.PollState;

/**
 * Thrown when the organizer tries to change what a poll is asking after voting is
 * over — its days, its invitees, or whether alternatives may be put forward.
 *
 * <p>Not the same refusal as {@link PollClosedException}, which is about an answer
 * arriving late. This one is about the question changing under answers that are
 * already final.
 */
public class PollNotEditableException extends IllegalStateException {

    PollNotEditableException(UUID id, PollState state) {
        super("Poll " + id + " is " + state + " and can no longer be edited");
    }
}
