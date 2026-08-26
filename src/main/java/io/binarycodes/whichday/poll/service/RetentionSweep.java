package io.binarycodes.whichday.poll.service;

import jakarta.annotation.PostConstruct;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import io.binarycodes.whichday.base.config.Retention;
import io.binarycodes.whichday.people.service.AccountDirectory;

/**
 * Runs the retention sweep and does nothing else. What it deletes and why belongs to the
 * two services it asks; this is only the thing that asks them, and the order matters —
 * the polls go first, so the names they were the last thing referring to are already
 * unreferenced by the time the accounts are looked at.
 *
 * <p>A fixed delay rather than a nightly cron. A cron at three in the morning is skipped
 * outright by a machine that is asleep then, and there is nothing about deleting rows
 * that wants a particular hour — where a fixed delay always runs shortly after start-up,
 * which is exactly when a deployment that has been down for a week needs it.
 *
 * <p>Conditional so that the test profile can turn it off: one Spring context serves the
 * whole suite and {@code TestDatabase} empties the tables between methods, so a
 * scheduled delete would be a race against whatever is running. The sweep is called
 * directly there, which is the boundary worth testing anyway.
 */
@Component
@ConditionalOnProperty(name = "whichday.retention.sweep", havingValue = "on", matchIfMissing = true)
class RetentionSweep {

    private static final Logger LOG = LoggerFactory.getLogger(RetentionSweep.class);

    private final PollService polls;
    private final AccountDirectory directory;
    private final Retention retention;

    RetentionSweep(PollService polls, AccountDirectory directory, Retention retention) {
        this.polls = polls;
        this.directory = directory;
        this.retention = retention;
    }

    /**
     * Said once at start-up, because these two windows are what decides whether a poll
     * somebody is looking for still exists — and the only other place they are written
     * down is the environment they arrived in.
     */
    @PostConstruct
    void announce() {
        LOG.info("Retention: after a poll ends = {}, since it was created = {}",
                retention.afterPollEnds(), retention.maximumAge());
    }

    /**
     * Logged when it deletes anything, because a poll that vanished is otherwise
     * something an operator can only discover from its absence.
     */
    @Scheduled(initialDelayString = "${whichday.retention.sweep-delay:PT1M}",
            fixedDelayString = "${whichday.retention.sweep-interval:PT24H}")
    void sweep() {
        var deletedPolls = polls.deleteExpiredPolls();
        var forgottenNames = directory.forgetExpiredAnonymous(polls.addressesOnAnyPoll());
        if (deletedPolls > 0 || forgottenNames > 0) {
            LOG.info("Retention: deleted {} poll(s) and {} anonymous name(s)",
                    deletedPolls, forgottenNames);
        } else {
            LOG.debug("Retention: nothing past its window");
        }
    }
}
