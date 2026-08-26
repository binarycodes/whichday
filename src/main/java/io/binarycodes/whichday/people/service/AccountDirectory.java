package io.binarycodes.whichday.people.service;

import java.time.Clock;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.binarycodes.whichday.base.config.Retention;
import io.binarycodes.whichday.people.domain.AnonymousAddress;
import io.binarycodes.whichday.people.domain.EmailAddress;
import io.binarycodes.whichday.people.domain.Person;

/**
 * The accounts that exist, which is exactly the people who have signed in. Nothing
 * seeds it and nothing else writes to it.
 *
 * <p>Deliberately not browsable: there is no method that returns everybody to a
 * screen, because the rule is that nobody is listed until the organizer has typed
 * enough of their address to have known who they were looking for. {@link #matching}
 * is the only way in, and it answers nothing under the minimum query length.
 * {@link #forInvite} turns an address with no account behind it into a person who can
 * still be invited.
 */
@Service
@Transactional(readOnly = true)
public class AccountDirectory implements PersonLookup {

    /** Below this a query would be a directory listing with extra steps. */
    public static final int MINIMUM_QUERY_LENGTH = 3;

    /** A long list is a directory too, so the answer is capped rather than paged. */
    private static final int MAXIMUM_MATCHES = 5;

    private final AccountRepository accounts;
    private final Clock clock;
    private final Retention retention;

    public AccountDirectory(AccountRepository accounts, Clock clock, Retention retention) {
        this.accounts = accounts;
        this.clock = clock;
        this.retention = retention;
    }

    /**
     * Remembers somebody the provider has vouched for, so that a colleague can find
     * them by address later. Re-recording updates the name, which is theirs to change;
     * a name that has not changed writes nothing, and this runs on every read of who
     * is looking.
     */
    @Transactional
    public void remember(Person person) {
        var email = EmailAddress.normalise(person.email());
        var stored = accounts.findById(email);
        if (stored.isEmpty()) {
            accounts.save(new StoredAccount(email, person.name(), clock.instant()));
            return;
        }
        stored.filter(account -> !account.name().equals(person.name()))
                .ifPresent(account -> account.rename(person.name()));
    }

    /**
     * Drops the names minted for anonymous sessions that nothing refers to any more, once
     * they have reached the same maximum age a poll has ({@code WHICHDAY_RETENTION_DAYS}).
     * Only those: an address a provider vouched for belongs to somebody who can come back
     * and be recognised, where a minted one belonged to a session that no longer exists.
     *
     * <p>Still referred to means still needed, whichever of the three ways it is — the
     * organizer of a poll, somebody invited, somebody who answered. A name is the account
     * table's alone, so deleting a row a live poll still mentions would leave that person
     * rendered as their own minted address on everybody else's screen.
     *
     * <p>The age is what makes it safe rather than merely tidy. A session that has just
     * said who it is has written its row and referred to nothing yet, so an unreferenced
     * row is not the same as an abandoned one until enough time has passed that no session
     * could still be holding it.
     *
     * <p>Loaded and filtered in Java, which is the same shape {@link #matching} is
     * criticised for and defensible here for the reason that one is not: this runs once a
     * sweep rather than once a keystroke.
     *
     * @param stillInUse every address any poll refers to
     * @return how many names were dropped
     */
    @Transactional
    public int forgetExpiredAnonymous(Set<String> stillInUse) {
        var cutoff = retention.maximumAge().cutoff(clock);
        if (cutoff.isEmpty()) {
            return 0;
        }
        var abandoned = accounts
                .findByEmailEndingWithAndCreatedAtBefore(AnonymousAddress.DOMAIN, cutoff.get()).stream()
                .filter(account -> !stillInUse.contains(account.email()))
                .toList();
        accounts.deleteAll(abandoned);
        return abandoned.size();
    }

    /**
     * Accounts whose address starts with the query, or one of whose name-parts does.
     * Never the domain: matching "acme" would hand back five colleagues to somebody
     * who guessed a company name, which is the listing this method exists to refuse.
     *
     * <p>The filtering stays in Java rather than becoming a {@code like} clause: the
     * part-splitting is not a prefix query, and this is where the rule about the domain
     * is written down.
     */
    public List<Person> matching(String query, Person searcher, List<Person> alreadyAdded) {
        if (query == null || query.strip().length() < MINIMUM_QUERY_LENGTH) {
            return List.of();
        }
        var needle = EmailAddress.normalise(query);
        return accounts.findAllByOrderByEmailAsc().stream()
                .map(AccountDirectory::personOf)
                .filter(account -> !account.equals(searcher))
                .filter(account -> alreadyAdded.stream().noneMatch(added -> added.email().equals(account.email())))
                .filter(account -> matches(account, needle))
                .limit(MAXIMUM_MATCHES)
                .toList();
    }

    /**
     * Whether the searcher's own account is what the query was reaching for. Matches
     * are silently short of the searcher, so a query that only they answer to comes
     * back empty and reads as "nobody by that name" — this is what lets a screen say
     * "that's you" instead. Telling somebody their own address is not a leak.
     *
     * <p>Reads nothing: the searcher is the answer, so there is nothing to look up.
     */
    public boolean matchesSearcher(String query, Person searcher) {
        return query != null
                && query.strip().length() >= MINIMUM_QUERY_LENGTH
                && matches(searcher, EmailAddress.normalise(query));
    }

    public Optional<Person> byEmail(String email) {
        return accounts.findById(EmailAddress.normalise(email)).map(AccountDirectory::personOf);
    }

    @Override
    public Person forInvite(String email) {
        return byEmail(email).orElseGet(() -> Person.outsider(EmailAddress.normalise(email)));
    }

    @Override
    public Map<String, Person> forInvites(Collection<String> emails) {
        var wanted = emails.stream().map(EmailAddress::normalise).distinct().toList();
        var known = accounts.findAllById(wanted).stream()
                .collect(Collectors.toMap(StoredAccount::email, AccountDirectory::personOf));
        return wanted.stream().collect(Collectors.toMap(Function.identity(),
                email -> known.getOrDefault(email, Person.outsider(email)),
                (first, second) -> first,
                LinkedHashMap::new));
    }

    /**
     * The tone is recomputed rather than stored: both {@code Person} factories derive
     * it from the address, so a column would only give a stored tone the chance to
     * disagree with a computed one.
     */
    private static Person personOf(StoredAccount account) {
        return Person.signedIn(account.email(), account.name());
    }

    private boolean matches(Person account, String needle) {
        var email = account.email().toLowerCase(Locale.ROOT);
        return email.startsWith(needle)
                || EmailAddress.searchableParts(email).stream().anyMatch(part -> part.startsWith(needle));
    }
}
