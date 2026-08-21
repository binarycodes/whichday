package io.binarycodes.whichday.poll.service;

import java.time.Clock;
import java.time.LocalDate;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.binarycodes.whichday.people.domain.EmailAddress;
import io.binarycodes.whichday.people.domain.Person;
import io.binarycodes.whichday.people.service.PersonLookup;
import io.binarycodes.whichday.poll.domain.Ballot;
import io.binarycodes.whichday.poll.domain.DayTally;
import io.binarycodes.whichday.poll.domain.Poll;
import io.binarycodes.whichday.poll.domain.PollState;
import io.binarycodes.whichday.poll.domain.PollSummary;

/**
 * Every poll there is, and the counting. State is rows in the database and nothing
 * seeds it — see {@code docs/clarifications/0010-an-embedded-database-on-disk.md} for
 * what it is and why it is not PostgreSQL.
 *
 * <p>Reads return immutable records built on the spot, so a screen holding one is
 * holding a snapshot rather than a view into the store. A poll stores addresses, and
 * the names come from the account table as the snapshot is built.
 *
 * <p>Readers go through {@code require}; writers go through {@code requireForUpdate},
 * which takes a row lock held until the transaction commits. That pairing is what
 * replaced a {@code synchronized} on every method: a monitor inside a transactional
 * proxy is released before the commit, so it reads like a guarantee and is not one.
 */
@Service
@Transactional(readOnly = true)
public class PollService {

    /** Whole days only, so a closing date rather than a closing moment. */
    private static final int MINIMUM_VOTING_DAYS = 1;

    private final Clock clock;
    private final PollRepository polls;
    private final PersonLookup people;

    public PollService(Clock clock, PollRepository polls, PersonLookup people) {
        this.clock = clock;
        this.polls = polls;
        this.people = people;
    }

    /**
     * A generated identifier rather than one made from the title: two teams may well
     * ask about "Team event" months apart, and a poll that inherited an existing
     * name would be a poll on top of somebody else's.
     */
    @Transactional
    public UUID create(String title, Person organizer, List<Person> invited) {
        var id = UUID.randomUUID();
        var invitees = invited.stream().map(PollService::addressOf).distinct().toList();
        polls.save(new StoredPoll(id, title, addressOf(organizer), invitees, clock.instant()));
        return id;
    }

    @Transactional
    public void replaceCandidateDays(UUID id, Collection<LocalDate> days) {
        requireForUpdate(id).replaceCandidateDays(days.stream().sorted().toList());
    }

    /** Sending the poll out is what opens it; until then it has no closing date. */
    @Transactional
    public void send(UUID id) {
        var stored = requireForUpdate(id);
        if (stored.closesOn() == null) {
            stored.closeOn(defaultClosingDayFor(stored), clock.instant());
        }
    }

    /**
     * The organizer's own closing date. Clamped to the range a closing date can
     * usefully be in — never in the past, and never on or after the first day being
     * voted on, because an answer that arrives then is about a day already gone.
     */
    @Transactional
    public void closeOn(UUID id, LocalDate day) {
        var stored = requireForUpdate(id);
        stored.closeOn(clampClosingDay(stored, day), clock.instant());
    }

    /**
     * When this poll would close if it went out now — what the share screen promises
     * before there is anything to promise it from.
     */
    public Optional<LocalDate> plannedClosing(UUID id) {
        var stored = require(id);
        if (stored.closesOn() != null) {
            return Optional.of(stored.closesOn());
        }
        return stored.candidateDays().isEmpty()
                ? Optional.empty()
                : Optional.of(defaultClosingDayFor(stored));
    }

    /** The last day the organizer may close on: the last day on the table. */
    public Optional<LocalDate> latestClosingDay(UUID id) {
        return lastDayOnTheTable(require(id));
    }

    @Transactional
    public void castVote(UUID id, Person voter, Set<LocalDate> chosenDays) {
        requireInvited(requireOpen(id), voter);
        record(id, voter, chosenDays);
    }

    /**
     * An answer without the open-for-answers check, for building polls that were
     * decided before the application started — history the public path is right to
     * refuse.
     *
     * <p>Package-private, so it carries no {@code @Transactional} of its own: Spring
     * would silently ignore one. It runs in its caller's transaction.
     */
    void record(UUID id, Person voter, Set<LocalDate> chosenDays) {
        var stored = require(id);
        var onTheTable = chosenDays.stream().filter(stored.candidateDays()::contains).sorted().toList();
        stored.record(addressOf(voter), new LinkedHashSet<>(onTheTable), List.of(), null);
    }

    /**
     * Answering that none of the days work. A counter-proposal is recorded against
     * the ballot rather than added to the candidate days: it becomes a column only
     * if the organizer accepts it.
     */
    @Transactional
    public void decline(UUID id, Person voter, List<LocalDate> proposedDays, String note) {
        requireInvited(requireOpen(id), voter)
                .record(addressOf(voter), Set.of(), proposedDays, note);
    }

    @Transactional
    public void acceptProposal(UUID id, LocalDate day) {
        var stored = requireForUpdate(id);
        var days = new LinkedHashSet<>(stored.candidateDays());
        days.add(day);
        stored.replaceCandidateDays(days.stream().sorted().toList());
    }

    @Transactional
    public void lock(UUID id, LocalDate day) {
        requireForUpdate(id).lock(day);
    }

    /**
     * The poll, if it is this viewer's to see at all. An address nobody invited gets
     * {@code Optional.empty()} — the same answer an id nobody issued gets, so a screen
     * cannot tell the two apart and neither can whoever is holding the link.
     */
    public Optional<Poll> poll(UUID id, Person viewer) {
        return polls.findVisibleById(id, addressOf(viewer)).map(this::snapshot);
    }

    /** Polls that are out with the team, whether or not they are still taking answers. */
    public List<PollSummary> openPolls(Person viewer) {
        return summaries(polls.findOpenVisibleTo(addressOf(viewer), PollRepository.CREATION_ORDER), viewer)
                .filter(summary -> !summary.isSettled() && !summary.isDraft())
                .toList();
    }

    /**
     * Polls this person named and never sent. Only theirs: a draft has been shown to
     * nobody, so it is not a poll anybody else has any business seeing. Scoped by
     * address in the query, where it used to compare whole people — so a draft whose
     * author has since corrected their name is no longer invisible to its author.
     */
    public List<PollSummary> draftPolls(Person viewer) {
        var mine = polls.findByOrganizerEmailAndLockedDayIsNull(addressOf(viewer), PollRepository.CREATION_ORDER);
        return summaries(mine, viewer).filter(PollSummary::isDraft).toList();
    }

    /**
     * Throws away a draft. Only a draft: a poll that has gone out has answers in it
     * and people waiting on it, and discarding one is a decision this does not make.
     */
    @Transactional
    public void deleteDraft(UUID id) {
        var stored = requireForUpdate(id);
        if (stateOf(stored) != PollState.DRAFT) {
            throw new IllegalStateException("Poll " + id + " has been sent and cannot be discarded");
        }
        polls.delete(stored);
    }

    /**
     * Nulls last rather than {@code Comparator.comparing} alone. A settled poll has a
     * locked day and so always has a headline day — but a row can now be written by
     * something other than this code, and a null there used to take the whole screen
     * down with a {@code NullPointerException}.
     */
    public List<PollSummary> settledPolls(Person viewer) {
        return summaries(polls.findSettledVisibleTo(addressOf(viewer), PollRepository.CREATION_ORDER), viewer)
                .filter(PollSummary::isSettled)
                .sorted(Comparator.comparing(PollSummary::headlineDay,
                                Comparator.nullsFirst(Comparator.<LocalDate>naturalOrder()))
                        .reversed())
                .toList();
    }

    /**
     * How often two people have answered the same poll — what tells an organizer that
     * a search hit is the colleague they meant rather than a stranger with a similar
     * address.
     */
    public int pollsSharedBy(Person viewer, Person other) {
        return Math.toIntExact(polls.countPollsAnsweredByBoth(addressOf(viewer), addressOf(other)));
    }

    /**
     * Whether voters may put other days forward. Turning it off does not take away
     * their ability to say none of the days work — that is an answer the organizer
     * needs either way — only the ask to add to the table.
     */
    @Transactional
    public void allowAlternatives(UUID id, boolean allowed) {
        requireForUpdate(id).allowAlternatives(allowed);
    }

    /** Somebody the organizer thought of after sending it out. */
    @Transactional
    public void addInvitee(UUID id, Person invitee) {
        requireForUpdate(id).invite(addressOf(invitee));
    }

    private Poll snapshot(StoredPoll stored) {
        return snapshot(stored, peopleFor(List.of(stored)));
    }

    private Poll snapshot(StoredPoll stored, Map<String, Person> named) {
        return new Poll(stored.id(),
                stored.title(),
                named.get(stored.organizerEmail()),
                invited(stored, named),
                List.copyOf(stored.candidateDays()),
                stored.closesOn(),
                stored.lockedDay(),
                stored.openedAt(),
                stored.alternativesAllowed(),
                stateOf(stored),
                tallies(stored, named),
                ballots(stored, named));
    }

    /**
     * One lookup for a whole list rather than one per person, so a screenful of polls
     * reads the account table once.
     */
    private Stream<PollSummary> summaries(List<StoredPoll> stored, Person viewer) {
        var named = peopleFor(stored);
        return stored.stream().map(poll -> summary(poll, viewer, named));
    }

    /**
     * Every address a snapshot will need a name for. An address with no account gets an
     * outsider, so nothing downstream has to handle a missing one — and because every
     * mention of one address in a snapshot comes from this map, two mentions of the
     * same person are equal, which is what {@code Poll.awaiting} compares.
     */
    private Map<String, Person> peopleFor(List<StoredPoll> stored) {
        var addresses = new LinkedHashSet<String>();
        stored.forEach(poll -> {
            addresses.add(poll.organizerEmail());
            addresses.addAll(poll.inviteeEmails());
            addresses.addAll(poll.ballots().keySet());
        });
        return people.forInvites(addresses);
    }

    private List<Person> invited(StoredPoll stored, Map<String, Person> named) {
        return stored.inviteeEmails().stream().map(named::get).toList();
    }

    /**
     * Ranked by vote count, and by date where two days tie — a stable order matters
     * because the rank is what the bars are painted from.
     */
    private List<DayTally> tallies(StoredPoll stored, Map<String, Person> named) {
        record Counted(LocalDate day, List<Person> voters) {
        }
        var counted = stored.candidateDays().stream()
                .map(day -> new Counted(day, votersFor(stored, named, day)))
                .sorted(Comparator.comparingInt((Counted entry) -> entry.voters().size()).reversed()
                        .thenComparing(Counted::day))
                .toList();
        var inviteCount = stored.inviteeEmails().size();
        return IntStream.range(0, counted.size())
                .mapToObj(index -> new DayTally(counted.get(index).day(),
                        counted.get(index).voters(),
                        index + 1,
                        inviteCount))
                .toList();
    }

    private List<Person> votersFor(StoredPoll stored, Map<String, Person> named, LocalDate day) {
        return stored.inviteeEmails().stream()
                .filter(email -> Optional.ofNullable(stored.ballots().get(email))
                        .map(ballot -> ballot.chosenDays().contains(day))
                        .orElse(false))
                .map(named::get)
                .toList();
    }

    private List<Ballot> ballots(StoredPoll stored, Map<String, Person> named) {
        return stored.inviteeEmails().stream()
                .map(email -> stored.ballots().get(email))
                .filter(Objects::nonNull)
                .map(ballot -> new Ballot(named.get(ballot.voterEmail()),
                        Set.copyOf(ballot.chosenDays()),
                        List.copyOf(ballot.proposedDays()),
                        ballot.note()))
                .toList();
    }

    private PollSummary summary(StoredPoll stored, Person viewer, Map<String, Person> named) {
        var poll = snapshot(stored, named);
        var headlineDay = poll.lockedDay() != null
                ? poll.lockedDay()
                : poll.leader().map(DayTally::day).orElse(null);
        return new PollSummary(poll.id(),
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
     * Which state the poll is in, settled here because this is what holds the clock.
     * A poll past its closing date has stopped collecting answers whether or not the
     * organizer has been back to lock a day in.
     *
     * <p>Derived rather than stored: a state column would be wrong from the moment the
     * clock crossed the closing date with nobody writing to the row, and the sweep that
     * would fix that is {@code docs/issues/0005-closing-happens-on-read.md}.
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
    private StoredPoll requireOpen(UUID id) {
        var stored = requireForUpdate(id);
        if (stateOf(stored) != PollState.OPEN) {
            throw new PollClosedException(id);
        }
        return stored;
    }

    /**
     * An answer only counts from somebody who was asked. Enforced here rather than only
     * by the screens, so that a stale tab, a replayed request or a forwarded link
     * cannot post one — and it throws the same {@code IllegalArgumentException} an
     * unknown id throws, because a person who was not invited should not be able to
     * learn from the difference that the poll is real.
     */
    private StoredPoll requireInvited(StoredPoll stored, Person voter) {
        if (!stored.inviteeEmails().contains(addressOf(voter))) {
            throw unknown(stored.id());
        }
        return stored;
    }

    private StoredPoll require(UUID id) {
        return polls.findById(id).orElseThrow(() -> unknown(id));
    }

    /** The same poll, with the row locked until this transaction commits. */
    private StoredPoll requireForUpdate(UUID id) {
        return polls.findWithLockById(id).orElseThrow(() -> unknown(id));
    }

    private static IllegalArgumentException unknown(UUID id) {
        return new IllegalArgumentException("No poll with id " + id);
    }

    /**
     * One canonical form for an address, because an address is the identity now.
     * {@code Person.outsider} does not normalise its argument, so without this a person
     * built from a mixed-case address would be written as a row nothing can find again.
     */
    private static String addressOf(Person person) {
        return EmailAddress.normalise(person.email());
    }
}
