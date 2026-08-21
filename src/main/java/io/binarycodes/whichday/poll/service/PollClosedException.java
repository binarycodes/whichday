package io.binarycodes.whichday.poll.service;

import java.util.UUID;

/** Thrown when an answer arrives for a poll that has stopped taking them. */
public class PollClosedException extends IllegalStateException {

    PollClosedException(UUID id) {
        super("Poll " + id + " is not open for answers");
    }
}
