package io.binarycodes.whichday.poll.domain;

/** Where a poll is in its life: being put together, out with the team, or settled. */
public enum PollState {
    DRAFT,
    OPEN,
    LOCKED
}
