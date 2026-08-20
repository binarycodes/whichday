package io.binarycodes.whichday.people.service;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

import org.springframework.stereotype.Service;

import io.binarycodes.whichday.people.domain.EmailAddress;
import io.binarycodes.whichday.people.domain.Person;

/**
 * The accounts that exist. Deliberately not browsable: there is no method that
 * returns everybody to a screen, because the design's rule is that nobody is listed
 * until the organizer has typed enough of their address to have known who they were
 * looking for.
 *
 * <p>{@link #matching} is the only way in, and it answers nothing under the minimum
 * query length. {@link #forInvite} turns an address with no account behind it into a
 * person who can still be invited and can still vote.
 */
@Service
public class AccountDirectory {

    /** Below this a query would be a directory listing with extra steps. */
    public static final int MINIMUM_QUERY_LENGTH = 3;

    /** A long list is a directory too, so the answer is capped rather than paged. */
    private static final int MAXIMUM_MATCHES = 5;

    private final List<Person> accounts = List.of(
            new Person("ada.lindqvist@acme.com", "Ada Lindqvist", 0),
            new Person("m.kallio@acme.com", "Miro Kallio", 0),
            new Person("sara.naslund@acme.com", "Sara Näslund", 1),
            new Person("tom.beck@acme.com", "Tom Beck", 2),
            new Person("priya.rao@acme.com", "Priya Rao", 3),
            new Person("jonas.wirtanen@acme.com", "Jonas Wirtanen", 2),
            new Person("lena.fors@acme.com", "Lena Fors", 1),
            new Person("t.sarkar@acme.com", "Tanvi Sarkar", 2),
            new Person("s.aronsson@acme.com", "Sixten Aronsson", 3));

    /**
     * Accounts whose address starts with the query, or one of whose name-parts does.
     * Never the domain: matching "acme" would hand back five colleagues to somebody
     * who guessed a company name, which is the listing this method exists to refuse.
     */
    public List<Person> matching(String query, Person searcher, List<Person> alreadyAdded) {
        if (query == null || query.strip().length() < MINIMUM_QUERY_LENGTH) {
            return List.of();
        }
        var needle = EmailAddress.normalise(query);
        return accounts.stream()
                .filter(account -> !account.equals(searcher))
                .filter(account -> alreadyAdded.stream().noneMatch(added -> added.email().equals(account.email())))
                .filter(account -> matches(account, needle))
                .limit(MAXIMUM_MATCHES)
                .toList();
    }

    public Optional<Person> byEmail(String email) {
        var wanted = EmailAddress.normalise(email);
        return accounts.stream().filter(account -> account.email().equals(wanted)).findFirst();
    }

    /** The account behind an address, or somebody new who is reachable at it anyway. */
    public Person forInvite(String email) {
        return byEmail(email).orElseGet(() -> Person.outsider(EmailAddress.normalise(email)));
    }

    /**
     * Everybody, for the account switcher that stands in for a login. Not a product
     * feature — see {@code docs/clarifications/0003-voter-identity.md} — and the one
     * caller goes away with it.
     */
    public List<Person> allForSwitcher() {
        return accounts;
    }

    public Person defaultViewer() {
        return accounts.getFirst();
    }

    private boolean matches(Person account, String needle) {
        var email = account.email().toLowerCase(Locale.ROOT);
        return email.startsWith(needle)
                || EmailAddress.searchableParts(email).stream().anyMatch(part -> part.startsWith(needle));
    }
}
