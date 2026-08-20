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
 */
public class TestClock extends Clock {

    private final ZoneId zone;

    private Instant now;

    public TestClock(Instant now, ZoneId zone) {
        this.now = now;
        this.zone = zone;
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
