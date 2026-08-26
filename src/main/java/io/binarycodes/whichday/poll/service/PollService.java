package io.binarycodes.whichday.poll.service;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.binarycodes.whichday.base.config.AccessMode;
import io.binarycodes.whichday.base.config.Retention;
import io.binarycodes.whichday.people.domain.EmailAddress;
import io.binarycodes.whichday.people.domain.Person;
import io.binarycodes.whichday.people.service.PersonLookup;
import io.binarycodes.whichday.poll.domain.Ballot;
import io.binarycodes.whichday.poll.domain.Caller;
import io.binarycodes.whichday.poll.domain.DayTally;
import io.binarycodes.whichday.poll.domain.Poll;
import io.binarycodes.whichday.poll.domain.PollState;
import io.binarycodes.whichday.poll.domain.PollSummary;

/**
 * Every poll there is, and the counting. State is rows in the database and nothing
 * seeds it — see {@code docs/REQUIREMENTS.md} §9 for what it is and why it is not
 * PostgreSQL.
 *
 * <p>Reads return immutable records built on the spot, so a screen holding one is
 * holding a snapshot rather than a view into the store. A poll stores addresses, and
 * the names come from the account table as the snapshot is built.
 *
 * <p>Readers go through {@code require}; writers go through {@code requireForUpdate},
 * which takes a row lock held until the transaction commits. That pairing is what
 * replaced a {@code synchronized} on every method: a monitor inside a transactional
 * proxy is released before the commit, so it reads like a guarantee and is not one.
 *
 * <p>Who may do what is decided here rather than by which button a screen draws:
 * reading needs an invitation, answering needs an invitation, and editing, settling or
 * discarding the poll needs to be the person who called it.
 *
 * <p>That is login mode. Anonymous mode has no invitations to check, so the link is
 * what stands in for one: anybody holding a poll's id may read it and answer it, a
 * voter joins the invitee list as they answer so that every count and stack downstream
 * keeps working, and changing the poll needs either the organizer's own session or the
 * six digits {@link Caller} carries. Three branches, all of them here, all of them
 * marked — nothing else in the application knows there are two modes of access.
 */
@Service
@Transactional(readOnly = true)
public class PollService {

    /** Whole days only, so a closing date rather than a closing moment. */
    private static final int MINIMUM_VOTING_DAYS = 1;

    /**
     * Six digits, leading zeros kept. Short enough to read down a phone line, which is
     * the point of it — and no shorter than that, because it is the only thing between
     * a stranger with the link and the poll's days.
     */
    private static final int ADMIN_CODE_BOUND = 1_000_000;
    private static final String ADMIN_CODE_SHAPE = "%06d";

    private final Clock clock;
    private final PollRepository polls;
    private final PersonLookup people;
    private final AccessMode access;
    private final Retention retention;
    private final SecureRandom codes = new SecureRandom();

    public PollService(Clock clock, PollRepository polls, PersonLookup people, AccessMode access,
                       Retention retention) {
        this.clock = clock;
        this.polls = polls;
        this.people = people;
        this.access = access;
        this.retention = retention;
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
        var stored = new StoredPoll(id, title, addressOf(organizer), invitees, clock.instant());
        if (access.isAnonymous()) {
            stored.useAdminCode(ADMIN_CODE_SHAPE.formatted(codes.nextInt(ADMIN_CODE_BOUND)));
        }
        polls.save(stored);
        return id;
    }

    /**
     * The code to keep, for the one screen that shows it. Empty in login mode, where no
     * poll has one.
     */
    public Optional<String> adminCodeOf(UUID id) {
        return Optional.ofNullable(require(id).adminCode());
    }

    @Transactional
    public void replaceCandidateDays(UUID id, Caller organizer, Collection<LocalDate> days) {
        requireEditable(requireOrganizer(id, organizer))
                .replaceCandidateDays(days.stream().sorted().toList());
    }

    /** Sending the poll out is what opens it; until then it has no closing date. */
    @Transactional
    public void send(UUID id, Caller organizer) {
        var stored = requireEditable(requireOrganizer(id, organizer));
        if (stored.closesOn() == null) {
            stored.closeOn(defaultClosingDayFor(stored), clock.instant());
        }
    }

    /**
     * The organizer's own closing date, while the poll is still taking answers.
     * Clamped to the range a closing date can usefully be in — never in the past, and
     * never past the last day on the table.
     */
    @Transactional
    public void closeOn(UUID id, Caller organizer, LocalDate day) {
        var stored = requireEditable(requireOrganizer(id, organizer));
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
        requireOpen(requireInvited(id, voter));
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
        requireOpen(requireInvited(id, voter))
                .record(addressOf(voter), Set.of(), proposedDays, note);
    }

    @Transactional
    public void acceptProposal(UUID id, Caller organizer, LocalDate day) {
        var stored = requireEditable(requireOrganizer(id, organizer));
        var days = new LinkedHashSet<>(stored.candidateDays());
        days.add(day);
        stored.replaceCandidateDays(days.stream().sorted().toList());
    }

    /**
     * Settling on a day. It has to happen while the poll is still open: a closing date
     * that has passed closes the poll to this as much as to anything else.
     */
    @Transactional
    public void lock(UUID id, Caller organizer, LocalDate day) {
        requireEditable(requireOrganizer(id, organizer)).lock(day);
    }

    /**
     * The poll, if it is this viewer's to see at all. An address nobody invited gets
     * {@code Optional.empty()} — the same answer an id nobody issued gets, so a screen
     * cannot tell the two apart and neither can whoever is holding the link.
     *
     * <p>A draft is the organizer's alone, the same rule {@link #draftPolls} applies.
     * The state is not a column, so this is the one predicate the query cannot carry:
     * {@code stateOf} decides it, here, rather than being restated in JPQL.
     *
     * <p>Anonymous mode has no invitee list to be on, so the id is the whole of the
     * question — holding the link is what being invited means there. A draft stays the
     * organizer's alone either way: it has been shown to nobody, so nobody has a link.
     */
    public Optional<Poll> poll(UUID id, Person viewer) {
        var email = addressOf(viewer);
        var found = access.isAnonymous() ? polls.findById(id) : polls.findVisibleById(id, email);
        return found
                .filter(stored -> stateOf(stored) != PollState.DRAFT
                        || stored.organizerEmail().equals(email))
                .map(this::snapshot);
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
    public void deleteDraft(UUID id, Caller organizer) {
        var stored = requireEditable(requireOrganizer(id, organizer));
        if (stateOf(stored) != PollState.DRAFT) {
            throw new IllegalStateException("Poll " + id + " has been sent and cannot be discarded");
        }
        polls.delete(stored);
    }

    /**
     * Throws away every poll a retention window has passed: a few days after it ended,
     * and unconditionally once it reaches its maximum age. Deleting a poll deletes
     * everything about it — the ballots, the invitations, the days and the answers —
     * so afterwards a link somebody saved reads exactly as a link to a poll that never
     * existed. That is the whole of what the screens have to do about it.
     *
     * <p>It takes no viewer because there is nobody to check: it is the one write here
     * that no person asked for, so §10b's question of who may do what has no subject.
     * The windows are the authority instead, and they are a deployment's to set.
     *
     * <p>Public deliberately, and not a candidate for tidying: Spring's proxy ignores
     * {@code @Transactional} on a method that is not public, and this one has to
     * override the read-only default the class carries — see {@link #record}, which
     * documents the same trap from the other side.
     *
     * <p>No row lock of its own, and it does not need one. Every poll the ended window
     * reaches is in a state {@link #requireEditable} and {@link #requireOpen} already
     * refuse every writer. The maximum age can take one somebody is still answering —
     * that is the ceiling doing what it is for — and the delete's own lock is what
     * settles the race: either the answer commits and the poll goes afterwards, or the
     * poll goes and the answer's locked read finds nothing, which is the same refusal a
     * link nobody issued gets.
     *
     * <p>A set rather than two lists, because a poll both windows reach is one poll. The
     * two queries run in one transaction, so the row they share comes back as the same
     * managed instance and identity is enough to hold it once.
     *
     * @return how many polls were deleted, for the caller to say so
     */
    @Transactional
    public int deleteExpiredPolls() {
        var today = LocalDate.now(clock);
        var doomed = new LinkedHashSet<StoredPoll>();
        retention.afterPollEnds().cutoff(today).ifPresent(cutoff -> doomed.addAll(endedBefore(cutoff)));
        retention.maximumAge().cutoff(clock)
                .ifPresent(cutoff -> doomed.addAll(polls.findByCreatedAtBefore(cutoff)));
        polls.deleteAll(doomed);
        return doomed.size();
    }

    /**
     * Polls that were over before the cutoff. The query narrows on the dates and this
     * decides what the dates mean, so what counts as over stays {@link #stateOf}'s
     * answer rather than becoming a second copy of it in JPQL.
     *
     * <p>The anchor is the later of the two days a poll can end on. A poll settled for a
     * day after it stopped taking answers has not happened yet, and deleting it on its
     * closing date would take away the answer the team comes back to it for.
     */
    private List<StoredPoll> endedBefore(LocalDate cutoff) {
        return polls.findEndedBefore(cutoff).stream()
                .filter(this::isOver)
                .filter(stored -> endedOn(stored).isBefore(cutoff))
                .toList();
    }

    private boolean isOver(StoredPoll stored) {
        var state = stateOf(stored);
        return state == PollState.CLOSED || state == PollState.LOCKED;
    }

    /** Only ever asked of a poll {@link #isOver} has already vouched for, so it has a day. */
    private static LocalDate endedOn(StoredPoll stored) {
        return Stream.of(stored.closesOn(), stored.lockedDay())
                .filter(Objects::nonNull)
                .max(LocalDate::compareTo)
                .orElseThrow();
    }

    /**
     * Every address any poll refers to, in any of the three ways one can: as the person
     * who called it, as somebody invited, or as somebody who answered. What the retention
     * sweep needs to know before it drops an account, since a name that is still on a
     * poll is a name that poll still has to show.
     *
     * <p>Whole columns rather than a join against the {@code account} table: the two are
     * deliberately not joined anywhere (§10 — an invitee may have no account at all), and
     * this keeps that true of the sweep as well.
     */
    public Set<String> addressesOnAnyPoll() {
        var addresses = new LinkedHashSet<>(polls.organizerAddresses());
        addresses.addAll(polls.inviteeAddresses());
        addresses.addAll(polls.voterAddresses());
        return addresses;
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
    public void allowAlternatives(UUID id, Caller organizer, boolean allowed) {
        requireEditable(requireOrganizer(id, organizer)).allowAlternatives(allowed);
    }

    /** Somebody the organizer thought of after sending it out. */
    @Transactional
    public void addInvitee(UUID id, Caller organizer, Person invitee) {
        requireEditable(requireOrganizer(id, organizer)).invite(addressOf(invitee));
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
     * Ordered by vote count, and by date where two days tie — a stable order matters
     * because the screens draw the list in it.
     *
     * <p>The rank is a competition rank, so days on the same count share one and their
     * bars are painted alike. It used to be the position in the list, which gave the
     * earlier of two tied days the leader's dark bar and the words that go with it.
     *
     * <p>And a day only <em>leads</em> when it is alone at the top. Three days on four
     * votes each are three days nobody has chosen between; calling the earliest of them
     * the most popular is the application inventing a result, and offering only that one
     * to be locked left the other two unreachable.
     */
    /**
     * Ordered by vote count, and by date where two days tie — a stable order matters
     * because the screens draw the list in it.
     *
     * <p>The rank is a competition rank (1, 1, 1, 4), so days on the same count share
     * one and their bars are painted alike. It used to be the position in the list,
     * which handed the earlier of two tied days the leader's dark bar and the words
     * that go with it.
     *
     * <p>And a day only <em>leads</em> when it is alone at the top. Three days on four
     * votes each are three days nobody has chosen between; naming the earliest of them
     * the most popular is the application inventing a result, and offering only that
     * one to be locked left the other two unreachable.
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
        var topCount = counted.isEmpty() ? 0 : counted.getFirst().voters().size();
        var aloneAtTheTop = topCount > 0
                && counted.stream().filter(entry -> entry.voters().size() == topCount).count() == 1;

        var tallies = new ArrayList<DayTally>(counted.size());
        var rank = 0;
        var previousCount = -1;
        for (var index = 0; index < counted.size(); index++) {
            var votes = counted.get(index).voters().size();
            if (votes != previousCount) {
                rank = index + 1;
                previousCount = votes;
            }
            tallies.add(new DayTally(counted.get(index).day(),
                    counted.get(index).voters(),
                    rank,
                    inviteCount,
                    aloneAtTheTop && votes == topCount));
        }
        return List.copyOf(tallies);
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
     * Nothing about a poll changes once voting is over. A {@code DRAFT} is still being
     * put together and an {@code OPEN} poll is still collecting, so both may be
     * changed. {@code CLOSED} and {@code LOCKED} are final states: no answer, no
     * invitation, no closing date, no locked day, nothing.
     *
     * <p>Every writing method goes through here, and that is the point — a rule with
     * exceptions in it is a rule somebody has to remember. Answers are refused by
     * {@link #requireOpen} instead, which says so in the voter's terms.
     *
     * <p>It also closes the way the days could be swapped under answers that were
     * already final. `replaceCandidateDays` prunes each ballot down to the days still
     * on the table, which is right while the poll is live and silent destruction after
     * it: replace every day at once and every yes becomes an empty ballot, with the
     * ballot row surviving so the voter still counted as having answered.
     */
    private StoredPoll requireEditable(StoredPoll stored) {
        var state = stateOf(stored);
        if (state != PollState.DRAFT && state != PollState.OPEN) {
            throw new PollNotEditableException(stored.id(), state);
        }
        return stored;
    }

    /**
     * A poll that has closed takes no more answers. Enforced here rather than only on
     * the screens, so that a stale tab cannot post one after the date has passed.
     *
     * <p>Takes a poll rather than an id, because it has to run *after* the caller has
     * been shown to be on it: telling somebody a poll has closed is telling them the
     * poll exists.
     */
    private StoredPoll requireOpen(StoredPoll stored) {
        if (stateOf(stored) != PollState.OPEN) {
            throw new PollClosedException(stored.id());
        }
        return stored;
    }

    /**
     * An answer only counts from somebody who was asked. Enforced here rather than only
     * by the screens, so that a stale tab, a replayed request or a forwarded link
     * cannot post one — and it throws the same {@code IllegalArgumentException} an
     * unknown id throws, because a person who was not invited should not be able to
     * learn from the difference that the poll is real.
     *
     * <p>Anonymous mode invites nobody, so answering is what puts a voter on the list
     * rather than the other way round. Adding them is not a formality: the tallies, the
     * avatar stacks and {@code Poll.awaiting} all read the invitee list, so a ballot
     * from somebody who is not on it would be counted nowhere.
     */
    private StoredPoll requireInvited(UUID id, Person voter) {
        var stored = requireForUpdate(id);
        var email = addressOf(voter);
        if (stored.inviteeEmails().contains(email)) {
            return stored;
        }
        if (access.isAnonymous()) {
            stored.invite(email);
            return stored;
        }
        throw unknown(id);
    }

    /**
     * Editing, settling and discarding a poll belong to the person who called it.
     * Enforced here because a hidden button is not a check: the screens do hide these
     * from everybody else, and that is a courtesy rather than the rule.
     *
     * <p>Three answers, not two. The organizer proceeds. Somebody who is on the poll is
     * refused by name, because they can already see it and there is nothing left to
     * withhold. Anybody else is told the poll does not exist — the refusal itself must
     * not be what reveals that it does.
     *
     * <p>The admin code is the fourth answer, and only anonymous mode has one. It is
     * checked against this poll's own code rather than looked up, so knowing six digits
     * is worth nothing without the link they go with. Login-mode polls have no code, and
     * the null check is what stops an absent one from matching an absent one.
     *
     * <p>Anonymous mode has only two answers, because the reason for the third is gone:
     * anybody who reached this call is holding the link, and the link already showed
     * them the poll. Withholding its existence from somebody looking at it would only
     * make the refusal read as a bug.
     */
    private StoredPoll requireOrganizer(UUID id, Caller asking) {
        var stored = requireForUpdate(id);
        var email = addressOf(asking.person());
        if (stored.organizerEmail().equals(email) || carriesAdminCode(stored, asking)) {
            return stored;
        }
        if (!access.isAnonymous() && !stored.inviteeEmails().contains(email)) {
            throw unknown(id);
        }
        throw new NotTheOrganizerException(id, email);
    }

    private static boolean carriesAdminCode(StoredPoll stored, Caller asking) {
        return stored.adminCode() != null && asking.adminCode().filter(stored.adminCode()::equals).isPresent();
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
