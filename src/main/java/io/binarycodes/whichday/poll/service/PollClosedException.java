package io.binarycodes.whichday.poll.service;

/** Thrown when an answer arrives for a poll that has stopped taking them. */
public class PollClosedException extends IllegalStateException {

    PollClosedException(String slug) {
        super("Poll " + slug + " is not open for answers");
    }
}
