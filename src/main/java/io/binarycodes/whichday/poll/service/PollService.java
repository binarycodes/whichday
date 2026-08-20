package io.binarycodes.whichday.poll.service;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
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

import io.binarycodes.whichday.people.domain.Person;
import io.binarycodes.whichday.poll.domain.Ballot;
import io.binarycodes.whichday.poll.domain.DayTally;
import io.binarycodes.whichday.poll.domain.Poll;
import io.binarycodes.whichday.poll.domain.PollState;
import io.binarycodes.whichday.poll.domain.PollSummary;

/**
 * Every poll there is, and the counting. State lives in memory for as long as the
 * application runs, and nothing seeds it — see
 * {@code docs/clarifications/0002-in-memory-store.md} for what that costs and what
 * replaces it.
 *
 * <p>Reads return immutable records built on the spot, so a screen holding one is
 * holding a snapshot rather than a view into the store.
 */
@Service
public class PollService {

    /** Whole days only, so a closing date rather than a closing moment. */
    private static final int MINIMUM_VOTING_DAYS = 1;

    private final Map<String, StoredPoll> polls = new LinkedHashMap<>();
    private final AtomicInteger slugSuffix = new AtomicInteger();
    private final Clock clock;

    public PollService(Clock clock) {
        this.clock = clock;
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
        if (stored.closesOn() == null) {
            stored.closeOn(defaultClosingDayFor(stored), clock.instant());
        }
    }

    /**
     * The organizer's own closing date. Clamped to the range a closing date can
     * usefully be in — never in the past, and never on or after the first day being
     * voted on, because an answer that arrives then is about a day already gone.
     */
    public synchronized void closeOn(String slug, LocalDate day) {
        var stored = require(slug);
        stored.closeOn(clampClosingDay(stored, day), clock.instant());
    }

    /**
     * When this poll would close if it went out now — what the share screen promises
     * before there is anything to promise it from.
     */
    public synchronized Optional<LocalDate> plannedClosing(String slug) {
        var stored = require(slug);
        if (stored.closesOn() != null) {
            return Optional.of(stored.closesOn());
        }
        return stored.candidateDays().isEmpty()
                ? Optional.empty()
                : Optional.of(defaultClosingDayFor(stored));
    }

    /** The last day the organizer may close on: the last day on the table. */
    public synchronized Optional<LocalDate> latestClosingDay(String slug) {
        return lastDayOnTheTable(require(slug));
    }

    public synchronized void castVote(String slug, Person voter, Set<LocalDate> chosenDays) {
        requireOpen(slug);
        record(slug, voter, chosenDays);
    }

    /**
     * An answer without the open-for-answers check. Only the sample seeder uses it,
     * to build polls that were decided before the application started — history the
     * public path is right to refuse.
     */
    synchronized void record(String slug, Person voter, Set<LocalDate> chosenDays) {
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
        requireOpen(slug).record(new StoredBallot(voter, Set.of(), proposedDays, note));
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

    /** Polls that are out with the team, whether or not they are still taking answers. */
    public synchronized List<PollSummary> openPolls(Person viewer) {
        return polls.values().stream()
                .map(stored -> summary(stored, viewer))
                .filter(summary -> !summary.isSettled() && !summary.isDraft())
                .toList();
    }

    /**
     * Polls this person named and never sent. Only theirs: a draft has been shown to
     * nobody, so it is not a poll anybody else has any business seeing.
     */
    public synchronized List<PollSummary> draftPolls(Person viewer) {
        return polls.values().stream()
                .map(stored -> summary(stored, viewer))
                .filter(PollSummary::isDraft)
                .filter(summary -> summary.askedBy().equals(viewer))
                .toList();
    }

    /**
     * Throws away a draft. Only a draft: a poll that has gone out has answers in it
     * and people waiting on it, and discarding one is a decision this does not make.
     */
    public synchronized void deleteDraft(String slug) {
        var stored = require(slug);
        if (stateOf(stored) != PollState.DRAFT) {
            throw new IllegalStateException("Poll " + slug + " has been sent and cannot be discarded");
        }
        polls.remove(slug);
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
                stored.closesOn(),
                stored.lockedDay(),
                stored.openedAt(),
                stored.alternativesAllowed(),
                stateOf(stored),
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
                .filter(person -> Optional.ofNullable(stored.ballots().get(person.email()))
                        .map(ballot -> ballot.chosenDays().contains(day))
                        .orElse(false))
                .toList();
    }

    private List<Ballot> ballots(StoredPoll stored) {
        return stored.invited().stream()
                .map(person -> stored.ballots().get(person.email()))
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
                poll.closesOn(),
                poll.candidateDays().size(),
                poll.answerCount(),
                poll.inviteCount(),
                poll.ballotOf(viewer).isPresent(),
                poll.state());
    }

    /**
     * How often two people have answered the same poll — what tells an organizer that
     * a search hit is the colleague they meant rather than a stranger with a similar
     * address.
     */
    public synchronized int pollsSharedBy(Person viewer, Person other) {
        return (int) polls.values().stream()
                .filter(stored -> stored.ballots().containsKey(viewer.email()))
                .filter(stored -> stored.ballots().containsKey(other.email()))
                .count();
    }

    /**
     * Whether voters may put other days forward. Turning it off does not take away
     * their ability to say none of the days work — that is an answer the organizer
     * needs either way — only the ask to add to the table.
     */
    public synchronized void allowAlternatives(String slug, boolean allowed) {
        require(slug).allowAlternatives(allowed);
    }

    /** Somebody the organizer thought of after sending it out. */
    public synchronized void addInvitee(String slug, Person invitee) {
        require(slug).invite(invitee);
    }

    /**
     * Which state the poll is in, settled here because this is what holds the clock.
     * A poll past its closing date has stopped collecting answers whether or not the
     * organizer has been back to lock a day in.
     */
    private PollState stateOf(StoredPoll stored) {
        if (stored.lockedDay() != null) {
            return PollState.LOCKED;
        }
        if (stored.candidateDays().isEmpty() || stored.closesOn() == null) {
            return PollState.DRAFT;
        }
        return LocalDate.now(clock).isAfter(stored.closesOn()) ? PollState.CLOSED : PollState.OPEN;
    }

    /**
     * Voting runs to the last day on the table, that day included.
     *
     * <p>It used to end before the *first* day, on the reasoning that answering about a
     * day already gone is pointless. That killed polls it had no business killing: with
     * five days offered, the first passing says nothing about the other four, and a team
     * that has not decided yet is exactly the team that still needs to. Days that have
     * passed drop out on their own — the ballot will not offer one — so the poll narrows
     * as it goes instead of dying at the first deadline.
     */
    private LocalDate defaultClosingDayFor(StoredPoll stored) {
        return clampClosingDay(stored, lastDayOnTheTable(stored).orElseThrow());
    }

    /**
     * A closing date leaves at least a day to answer in and never runs past the last day
     * on the table. When those two cannot both hold — the last option is today or
     * tomorrow — the last day on the table wins.
     */
    private LocalDate clampClosingDay(StoredPoll stored, LocalDate wanted) {
        var latest = lastDayOnTheTable(stored).orElse(wanted);
        var floor = LocalDate.now(clock).plusDays(MINIMUM_VOTING_DAYS);
        var chosen = wanted.isBefore(floor) ? floor : wanted;
        return chosen.isAfter(latest) ? latest : chosen;
    }

    private Optional<LocalDate> lastDayOnTheTable(StoredPoll stored) {
        return stored.candidateDays().stream().max(LocalDate::compareTo);
    }

    /**
     * A poll that has closed takes no more answers. Enforced here rather than only on
     * the screens, so that a stale tab cannot post one after the date has passed.
     */
    private StoredPoll requireOpen(String slug) {
        var stored = require(slug);
        if (stateOf(stored) != PollState.OPEN) {
            throw new PollClosedException(slug);
        }
        return stored;
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
