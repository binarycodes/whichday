package io.binarycodes.whichday;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;

/**
 * A clock the test moves. State that depends on a date — a poll closing the day
 * after its closing date — cannot be checked with {@link Clock#fixed}: the service
 * holds one clock instance for its lifetime, so the only way to reach tomorrow is a
 * clock that can be told to go there.
 *
 * <p>One instance now serves the whole suite, because it is a bean in a shared
 * context — so it has an origin to be sent back to between methods.
 */
public class TestClock extends Clock {

    /** What "today" is for every test that has an opinion about it. */
    public static final Instant START = Instant.parse("2026-08-20T09:00:00Z");

    private final ZoneId zone;
    private final Instant origin;

    private Instant now;

    public TestClock(Instant now, ZoneId zone) {
        this.now = now;
        this.origin = now;
        this.zone = zone;
    }

    /** Back to where it started, so one test's tomorrow is not the next one's today. */
    public void reset() {
        this.now = origin;
    }

    public void advanceBy(Duration elapsed) {
        this.now = now.plus(elapsed);
    }

    public void advanceDays(long days) {
        advanceBy(Duration.ofDays(days));
    }

    @Override
    public Instant instant() {
        return now;
    }

    @Override
    public ZoneId getZone() {
        return zone;
    }

    @Override
    public Clock withZone(ZoneId other) {
        return new TestClock(now, other);
    }
}
