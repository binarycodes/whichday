package io.binarycodes.whichday.people.service;

import java.time.Instant;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

/** Package-private, and never seen above this package (CODING_CONVENTIONS.md §10). */
interface AccountRepository extends JpaRepository<StoredAccount, String> {

    /**
     * Ordered by address rather than by when they signed in. Sign-in order was what a
     * {@code LinkedHashMap} happened to give, and it decided which five of six matches
     * a search returned — a tie-break nobody chose. Alphabetical is at least the same
     * answer twice.
     */
    List<StoredAccount> findAllByOrderByEmailAsc();

    /**
     * Accounts minted for a session that are old enough to be swept. Which of them are
     * still spoken for is not a question this table can answer — a poll refers to an
     * address, never to an account row — so the directory decides that part.
     */
    List<StoredAccount> findByEmailEndingWithAndCreatedAtBefore(String domain, Instant cutoff);
}
