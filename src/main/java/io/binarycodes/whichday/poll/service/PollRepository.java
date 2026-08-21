package io.binarycodes.whichday.poll.service;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;

/** Package-private, and never seen above this package (CODING_CONVENTIONS.md §10). */
interface PollRepository extends JpaRepository<StoredPoll, UUID> {

    /**
     * Who may see a poll: the person who asked, and the people they asked. Matched by
     * address rather than by account, because an invitee may never have signed in —
     * there is no id to compare. Both arms are indexed ({@code idx_poll_organizer},
     * {@code idx_poll_invitee_email}).
     *
     * <p>The organizer arm is not redundant with the invitee arm even though
     * {@code createFromDraft} puts them first in the list: {@code create} does not
     * enforce that, and a poll whose author is somehow not on its own invitee list
     * should still be readable by them.
     */
    String VISIBLE_TO = """
            select poll from StoredPoll poll
            where (poll.organizerEmail = :email or :email member of poll.inviteeEmails)
            """;

    /**
     * The order the three list screens render, which is the order the polls were
     * created in. The id breaks a tie, because a frozen clock gives several polls the
     * same instant.
     */
    Sort CREATION_ORDER = Sort.by(Sort.Order.asc("createdAt"), Sort.Order.asc("id"));

    /**
     * The poll a writer loads. The row lock is what replaces the service's instance
     * monitor: held until the transaction commits, and scoped to the one poll being
     * changed rather than to every poll there is. Every writer takes exactly one, so
     * there is no order to deadlock over.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<StoredPoll> findWithLockById(UUID id);

    /**
     * The one poll, and only to somebody who is on it. An address nobody invited gets
     * an empty result — the same answer an id nobody issued gets, which is the point:
     * whether the poll exists is not something a stranger's screen should reveal.
     */
    @Query(VISIBLE_TO + " and poll.id = :id")
    Optional<StoredPoll> findVisibleById(@Param("id") UUID id, @Param("email") String email);

    /**
     * Narrowed on the half of the question that does not involve the clock — sent, and
     * not settled. Which of those are still taking answers is {@code stateOf}'s to say.
     */
    @Query(VISIBLE_TO + " and poll.lockedDay is null and poll.closesOn is not null")
    List<StoredPoll> findOpenVisibleTo(@Param("email") String email, Sort sort);

    @Query(VISIBLE_TO + " and poll.lockedDay is not null")
    List<StoredPoll> findSettledVisibleTo(@Param("email") String email, Sort sort);

    /** Drafts are the organizer's alone, so this needs no invitee arm. */
    List<StoredPoll> findByOrganizerEmailAndLockedDayIsNull(String organizerEmail, Sort sort);

    /**
     * How many polls two addresses have both answered. A query rather than a scan
     * because a search runs this once per hit on every keystroke past three characters.
     */
    @Query("""
           select count(mine) from StoredBallot mine
           where mine.voterEmail = :one
             and exists (select 1 from StoredBallot theirs
                         where theirs.poll = mine.poll and theirs.voterEmail = :other)
           """)
    long countPollsAnsweredByBoth(@Param("one") String one, @Param("other") String other);
}
