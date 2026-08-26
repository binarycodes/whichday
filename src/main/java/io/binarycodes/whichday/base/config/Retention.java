package io.binarycodes.whichday.base.config;

/**
 * How long a poll is kept. Two windows, and a poll goes when either one has passed it.
 *
 * <p>{@link #afterPollEnds} is the ordinary path: a poll that is over goes a few days
 * later, measured from the day it ended. {@link #maximumAge} is a ceiling measured from
 * the day it was created, and it reaches every poll there is — a draft nobody sent, a
 * poll still taking answers, a settled one whose day is still ahead. Nothing survives
 * it, which is the point of it: the store is one file nothing else prunes ({@code
 * docs/REQUIREMENTS.md} §9), so without a rule that reaches every row there are rows
 * no rule reaches.
 *
 * <p>The ceiling therefore wins where the two disagree, and a deployment that sets
 * {@code afterPollEnds} beyond it has made the longer window unreachable rather than
 * made a mistake worth refusing at startup.
 *
 * <p>Both may be {@link RetentionWindow#NEVER} independently, which is how a deployment
 * keeps everything for good.
 */
public record Retention(RetentionWindow afterPollEnds, RetentionWindow maximumAge) {
}
