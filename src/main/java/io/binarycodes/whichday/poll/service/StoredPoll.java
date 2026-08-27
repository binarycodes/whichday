package io.binarycodes.whichday.poll.service;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.UUID;

import org.hibernate.annotations.BatchSize;
import org.hibernate.annotations.SortNatural;
import org.springframework.data.domain.Persistable;

import jakarta.persistence.CascadeType;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MapKey;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;

/**
 * The mutable poll the service keeps. Package-private and never returned: callers
 * see the immutable {@code Poll} the service builds from one, which is what keeps
 * a stateful UI from holding a half-updated aggregate.
 *
 * <p>People are addresses here. The names are the account table's, fetched when a
 * snapshot is built — see {@code docs/REQUIREMENTS.md} §10.
 *
 * <p>No Lombok, deliberately. Hibernate wants a non-final class and a no-arg
 * constructor, and it reads fields directly — it does not want accessors. Generated
 * ones would put a {@code setOpenedAt} next to {@link #closeOn}, which stamps once,
 * and a {@code setCandidateDays} next to {@link #replaceCandidateDays}, which prunes
 * the ballots as it goes. Those two are rules, not boilerplate.
 */
@Entity
@Table(name = "poll")
class StoredPoll implements Persistable<UUID> {

    /** Kept here because the column declares it; the screen's own constant matches. */
    static final int TITLE_LENGTH = 50;

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    /**
     * Fifty, the same maximum the field that collects it now sets. The pair is the point:
     * a limit in the schema that no field shows is a save that fails for no stated reason,
     * and a limit on the field alone is one a crafted value walks past.
     */
    @Column(name = "title", length = TITLE_LENGTH, nullable = false)
    private String title;

    @Column(name = "organizer_email", length = 320, nullable = false)
    private String organizerEmail;

    @Column(name = "closes_on")
    private LocalDate closesOn;

    @Column(name = "locked_day")
    private LocalDate lockedDay;

    @Column(name = "opened_at")
    private Instant openedAt;

    /** What the list screens are ordered by, which used to be map insertion order. */
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "alternatives_allowed", nullable = false)
    private boolean alternativesAllowed = true;

    /**
     * What lets somebody who is not the organizer change this poll anyway. Written
     * only in anonymous mode, where a session that closed its tab has no other way
     * back; null on every login-mode poll.
     */
    @Column(name = "admin_code", length = 6)
    private String adminCode;

    /**
     * Sorted rather than insertion-ordered: every write already sorts, so this is the
     * order the days were in anyway, and it no longer depends on what order the rows
     * come back in.
     */
    @ElementCollection
    @CollectionTable(name = "candidate_day", joinColumns = @JoinColumn(name = "poll_id"))
    @Column(name = "offered_day", nullable = false)
    @SortNatural
    private SortedSet<LocalDate> candidateDays = new TreeSet<>();

    @ElementCollection
    @CollectionTable(name = "poll_invitee", joinColumns = @JoinColumn(name = "poll_id"))
    @OrderColumn(name = "ordinal")
    @Column(name = "email", length = 320, nullable = false)
    private List<String> inviteeEmails = new ArrayList<>();

    @OneToMany(mappedBy = "poll", cascade = CascadeType.ALL, orphanRemoval = true)
    @MapKey(name = "voterEmail")
    @BatchSize(size = 50)
    private Map<String, StoredBallot> ballots = new LinkedHashMap<>();

    @Transient
    private boolean unsaved = true;

    protected StoredPoll() {
    }

    StoredPoll(UUID id, String title, String organizerEmail, List<String> inviteeEmails, Instant createdAt) {
        this.id = id;
        this.title = title;
        this.organizerEmail = organizerEmail;
        this.createdAt = createdAt;
        inviteeEmails.forEach(this::invite);
    }

    /**
     * Says "new" until it has been loaded or written, so that {@code save} inserts
     * rather than merges. A merge on a taken id is a silent {@code update}, which is
     * one poll landing on top of another; an insert is a primary-key violation, which
     * is somebody being told.
     */
    @Override
    public boolean isNew() {
        return unsaved;
    }

    @PostLoad
    @PrePersist
    void tracked() {
        unsaved = false;
    }

    @Override
    public UUID getId() {
        return id;
    }

    UUID id() {
        return id;
    }

    String title() {
        return title;
    }

    String organizerEmail() {
        return organizerEmail;
    }

    String adminCode() {
        return adminCode;
    }

    void useAdminCode(String code) {
        this.adminCode = code;
    }

    List<String> inviteeEmails() {
        return List.copyOf(inviteeEmails);
    }

    /** Adding somebody twice would double every denominator they appear in. */
    void invite(String email) {
        if (!inviteeEmails.contains(email)) {
            inviteeEmails.add(email);
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

    LocalDate closesOn() {
        return closesOn;
    }

    /** Setting a closing date is what sends the poll out, so it is also what stamps it. */
    void closeOn(LocalDate day, Instant sentAt) {
        this.closesOn = day;
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

    void record(String voterEmail, Set<LocalDate> chosenDays, List<LocalDate> proposedDays) {
        ballots.computeIfAbsent(voterEmail, email -> new StoredBallot(this, email))
                .answer(chosenDays, proposedDays);
    }
}
