package io.binarycodes.whichday.poll.domain;

/** Where a poll is in its life: being put together, out with the team, over, or settled. */
public enum PollState {
    DRAFT,
    OPEN,
    /** Past its closing date with no day locked in — waiting on the organizer. */
    CLOSED,
    LOCKED
}
