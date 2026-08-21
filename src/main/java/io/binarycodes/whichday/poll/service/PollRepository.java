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
     * Narrowed on the half of the question that does not involve the clock — sent, and
     * not settled. Which of those are still taking answers is {@code stateOf}'s to say.
     */
    List<StoredPoll> findByLockedDayIsNullAndClosesOnIsNotNull(Sort sort);

    List<StoredPoll> findByOrganizerEmailAndLockedDayIsNull(String organizerEmail, Sort sort);

    List<StoredPoll> findByLockedDayIsNotNull(Sort sort);

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
