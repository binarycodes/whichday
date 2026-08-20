package io.binarycodes.findadate.poll.service;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.TemporalAdjusters;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;

import org.springframework.stereotype.Service;

import io.binarycodes.findadate.people.domain.Person;
import io.binarycodes.findadate.people.service.TeamDirectory;
import io.binarycodes.findadate.poll.domain.Ballot;
import io.binarycodes.findadate.poll.domain.DayTally;
import io.binarycodes.findadate.poll.domain.Poll;
import io.binarycodes.findadate.poll.domain.PollSummary;

/**
 * Every poll there is, and the counting. State lives in memory for as long as the
 * application runs — see {@code docs/clarifications/0002-in-memory-store.md} for
 * what that costs and what replaces it.
 *
 * <p>Reads return immutable records built on the spot, so a screen holding one is
 * holding a snapshot rather than a view into the store.
 */
@Service
public class PollService {

    /** Whole days only, and voting closes at the end of a working afternoon. */
    private static final LocalTime CLOSING_TIME = LocalTime.of(18, 0);
    private static final int DEFAULT_VOTING_DAYS = 5;

    private final Map<String, StoredPoll> polls = new LinkedHashMap<>();
    private final AtomicInteger slugSuffix = new AtomicInteger();
    private final Clock clock;

    public PollService(Clock clock, TeamDirectory directory) {
        this.clock = clock;
        SampleData.seed(this, directory, clock);
    }

    public synchronized String create(String title, Person organizer, List<Person> invited) {
        var slug = slugFor(title);
        polls.put(slug, new StoredPoll(slug, title, organizer, invited));
        return slug;
    }

    public synchronized void replaceCandidateDays(String slug, Collection<LocalDate> days) {
        var stored = require(slug);
        stored.replaceCandidateDays(days.stream().sorted().toList());
    }

    /** Sending the poll out is what opens it; until then it has no closing date. */
    public synchronized void send(String slug) {
        var stored = require(slug);
        if (stored.closesAt() == null) {
            stored.closeAt(defaultClosingMoment(), clock.instant());
        }
    }

    public synchronized void closeAt(String slug, LocalDateTime moment) {
        require(slug).closeAt(moment, clock.instant());
    }

    public synchronized void castVote(String slug, Person voter, Set<LocalDate> chosenDays) {
        var stored = require(slug);
        var onTheTable = chosenDays.stream().filter(stored.candidateDays()::contains).sorted().toList();
        stored.record(new StoredBallot(voter, new LinkedHashSet<>(onTheTable), List.of(), null));
    }

    /**
     * Answering that none of the days work. A counter-proposal is recorded against
     * the ballot rather than added to the candidate days: it becomes a column only
     * if the organizer accepts it.
     */
    public synchronized void decline(String slug, Person voter, List<LocalDate> proposedDays, String note) {
        require(slug).record(new StoredBallot(voter, Set.of(), proposedDays, note));
    }

    public synchronized void acceptProposal(String slug, LocalDate day) {
        var stored = require(slug);
        var days = new LinkedHashSet<>(stored.candidateDays());
        days.add(day);
        stored.replaceCandidateDays(days.stream().sorted().toList());
    }

    public synchronized void lock(String slug, LocalDate day) {
        require(slug).lock(day);
    }

    public synchronized Optional<Poll> poll(String slug) {
        return Optional.ofNullable(polls.get(slug)).map(this::snapshot);
    }

    /** Polls still waiting on somebody, newest first. */
    public synchronized List<PollSummary> openPolls(Person viewer) {
        return polls.values().stream()
                .map(stored -> summary(stored, viewer))
                .filter(summary -> !summary.isSettled())
                .toList();
    }

    public synchronized List<PollSummary> settledPolls(Person viewer) {
        return polls.values().stream()
                .map(stored -> summary(stored, viewer))
                .filter(PollSummary::isSettled)
                .sorted(Comparator.comparing(PollSummary::headlineDay).reversed())
                .toList();
    }

    private Poll snapshot(StoredPoll stored) {
        return new Poll(stored.slug(),
                stored.title(),
                stored.organizer(),
                stored.invited(),
                List.copyOf(stored.candidateDays()),
                stored.closesAt(),
                stored.lockedDay(),
                stored.openedAt(),
                tallies(stored),
                ballots(stored));
    }

    /**
     * Ranked by vote count, and by date where two days tie — a stable order matters
     * because the rank is what the bars are painted from.
     */
    private List<DayTally> tallies(StoredPoll stored) {
        record Counted(LocalDate day, List<Person> voters) {
        }
        var counted = stored.candidateDays().stream()
                .map(day -> new Counted(day, votersFor(stored, day)))
                .sorted(Comparator.comparingInt((Counted entry) -> entry.voters().size()).reversed()
                        .thenComparing(Counted::day))
                .toList();
        var inviteCount = stored.invited().size();
        return IntStream.range(0, counted.size())
                .mapToObj(index -> new DayTally(counted.get(index).day(),
                        counted.get(index).voters(),
                        index + 1,
                        inviteCount))
                .toList();
    }

    private List<Person> votersFor(StoredPoll stored, LocalDate day) {
        return stored.invited().stream()
                .filter(person -> Optional.ofNullable(stored.ballots().get(person.id()))
                        .map(ballot -> ballot.chosenDays().contains(day))
                        .orElse(false))
                .toList();
    }

    private List<Ballot> ballots(StoredPoll stored) {
        return stored.invited().stream()
                .map(person -> stored.ballots().get(person.id()))
                .filter(Objects::nonNull)
                .map(ballot -> new Ballot(ballot.voter(),
                        Set.copyOf(ballot.chosenDays()),
                        ballot.proposedDays(),
                        ballot.note()))
                .toList();
    }

    private PollSummary summary(StoredPoll stored, Person viewer) {
        var poll = snapshot(stored);
        var headlineDay = poll.lockedDay() != null
                ? poll.lockedDay()
                : poll.leader().map(DayTally::day).orElse(null);
        return new PollSummary(poll.slug(),
                poll.title(),
                poll.organizer(),
                headlineDay,
                poll.candidateDays().stream().findFirst().orElse(headlineDay),
                poll.closesAt(),
                poll.candidateDays().size(),
                poll.answerCount(),
                poll.inviteCount(),
                poll.ballotOf(viewer).isPresent(),
                poll.state());
    }

    private LocalDateTime defaultClosingMoment() {
        return LocalDate.now(clock)
                .plusDays(DEFAULT_VOTING_DAYS)
                .with(TemporalAdjusters.nextOrSame(DayOfWeek.FRIDAY))
                .atTime(CLOSING_TIME);
    }

    private StoredPoll require(String slug) {
        var stored = polls.get(slug);
        if (stored == null) {
            throw new IllegalArgumentException("No poll with slug " + slug);
        }
        return stored;
    }

    /**
     * A readable URL rather than an identifier, because the design puts the slug on
     * the screen as the voting link. Collisions get a counter rather than a retry:
     * two polls may legitimately share a title.
     */
    private String slugFor(String title) {
        var base = title.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-").replaceAll("(^-|-$)", "");
        var candidate = base.isEmpty() ? "poll" : base;
        return polls.containsKey(candidate) ? candidate + "-" + slugSuffix.incrementAndGet() : candidate;
    }
}
