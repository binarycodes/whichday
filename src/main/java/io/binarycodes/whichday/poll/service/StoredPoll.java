package io.binarycodes.whichday.poll.service;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import io.binarycodes.whichday.people.domain.Person;

/**
 * The mutable poll the service keeps. Package-private and never returned: callers
 * see the immutable {@code Poll} the service builds from one, which is what keeps
 * a stateful UI from holding a half-updated aggregate.
 */
class StoredPoll {

    private final String slug;
    private final Person organizer;
    private final List<Person> invited = new ArrayList<>();
    private final Map<String, StoredBallot> ballots = new LinkedHashMap<>();
    private final Set<LocalDate> candidateDays = new LinkedHashSet<>();

    private String title;
    private LocalDateTime closesAt;
    private LocalDate lockedDay;
    private Instant openedAt;
    private boolean alternativesAllowed = true;

    StoredPoll(String slug, String title, Person organizer, List<Person> invited) {
        this.slug = slug;
        this.title = title;
        this.organizer = organizer;
        invited.forEach(this::invite);
    }

    String slug() {
        return slug;
    }

    String title() {
        return title;
    }

    void rename(String newTitle) {
        this.title = newTitle;
    }

    Person organizer() {
        return organizer;
    }

    List<Person> invited() {
        return List.copyOf(invited);
    }

    /** Adding somebody twice would double every denominator they appear in. */
    void invite(Person person) {
        if (invited.stream().noneMatch(existing -> existing.email().equals(person.email()))) {
            invited.add(person);
        }
    }

    Set<LocalDate> candidateDays() {
        return candidateDays;
    }

    /**
     * Replacing the candidate days drops any vote for a day that is no longer on
     * the table — a tally for a withdrawn day would otherwise keep being counted.
     */
    void replaceCandidateDays(Iterable<LocalDate> days) {
        candidateDays.clear();
        days.forEach(candidateDays::add);
        ballots.values().forEach(ballot -> ballot.chosenDays().retainAll(candidateDays));
    }

    LocalDateTime closesAt() {
        return closesAt;
    }

    /** Setting a closing date is what sends the poll out, so it is also what stamps it. */
    void closeAt(LocalDateTime moment, Instant sentAt) {
        this.closesAt = moment;
        if (this.openedAt == null) {
            this.openedAt = sentAt;
        }
    }

    Instant openedAt() {
        return openedAt;
    }

    LocalDate lockedDay() {
        return lockedDay;
    }

    void lock(LocalDate day) {
        this.lockedDay = day;
    }

    boolean alternativesAllowed() {
        return alternativesAllowed;
    }

    void allowAlternatives(boolean allowed) {
        this.alternativesAllowed = allowed;
    }

    Map<String, StoredBallot> ballots() {
        return ballots;
    }

    void record(StoredBallot ballot) {
        ballots.put(ballot.voter().email(), ballot);
    }
}
