package io.binarycodes.whichday.poll.service;

import java.util.List;

import org.springframework.stereotype.Service;

import io.binarycodes.whichday.people.domain.Person;
import io.binarycodes.whichday.people.service.AccountDirectory;
import io.binarycodes.whichday.poll.domain.AccountMatch;

/**
 * Who the organizer might mean. Lives with the polls rather than with the accounts
 * because the interesting half of the answer — how often you and somebody have
 * decided a date together — is poll knowledge.
 */
@Service
public class InviteeSearch {

    private final AccountDirectory accounts;
    private final PollService polls;

    public InviteeSearch(AccountDirectory accounts, PollService polls) {
        this.accounts = accounts;
        this.polls = polls;
    }

    public List<AccountMatch> matching(String query, Person searcher, List<Person> alreadyAdded) {
        return accounts.matching(query, searcher, alreadyAdded).stream()
                .map(person -> new AccountMatch(person, polls.pollsSharedBy(searcher, person)))
                .sorted((left, right) -> Integer.compare(right.sharedPolls(), left.sharedPolls()))
                .toList();
    }

    /** Whether the query was reaching for the organizer's own account. */
    public boolean matchesSearcher(String query, Person searcher) {
        return accounts.matchesSearcher(query, searcher);
    }

    /** The address itself, once it is one — whether or not an account answers to it. */
    public Person inviteFor(String email) {
        return accounts.forInvite(email);
    }

    public boolean hasAccount(String email) {
        return accounts.byEmail(email).isPresent();
    }
}
