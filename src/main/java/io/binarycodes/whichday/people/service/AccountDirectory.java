package io.binarycodes.whichday.people.service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import org.springframework.stereotype.Service;

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
public class AccountDirectory {

    /** Below this a query would be a directory listing with extra steps. */
    public static final int MINIMUM_QUERY_LENGTH = 3;

    /** A long list is a directory too, so the answer is capped rather than paged. */
    private static final int MAXIMUM_MATCHES = 5;

    /**
     * Everybody who has signed in, newest last. There is no other source: an account
     * exists because somebody authenticated, not because a list said so.
     */
    private final Map<String, Person> accounts = new LinkedHashMap<>();

    /**
     * Remembers somebody the provider has vouched for, so that a colleague can find
     * them by address later. Re-recording updates the name, which is theirs to change.
     */
    public synchronized void remember(Person person) {
        accounts.put(person.email(), person);
    }

    /**
     * Accounts whose address starts with the query, or one of whose name-parts does.
     * Never the domain: matching "acme" would hand back five colleagues to somebody
     * who guessed a company name, which is the listing this method exists to refuse.
     */
    public synchronized List<Person> matching(String query, Person searcher, List<Person> alreadyAdded) {
        if (query == null || query.strip().length() < MINIMUM_QUERY_LENGTH) {
            return List.of();
        }
        var needle = EmailAddress.normalise(query);
        return accounts.values().stream()
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
     */
    public boolean matchesSearcher(String query, Person searcher) {
        return query != null
                && query.strip().length() >= MINIMUM_QUERY_LENGTH
                && matches(searcher, EmailAddress.normalise(query));
    }

    public synchronized Optional<Person> byEmail(String email) {
        return Optional.ofNullable(accounts.get(EmailAddress.normalise(email)));
    }

    /** The account behind an address, or somebody new who is reachable at it anyway. */
    public Person forInvite(String email) {
        return byEmail(email).orElseGet(() -> Person.outsider(EmailAddress.normalise(email)));
    }

    private boolean matches(Person account, String needle) {
        var email = account.email().toLowerCase(Locale.ROOT);
        return email.startsWith(needle)
                || EmailAddress.searchableParts(email).stream().anyMatch(part -> part.startsWith(needle));
    }
}
