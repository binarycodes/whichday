package io.binarycodes.whichday.people.service;

import java.util.Collection;
import java.util.Map;

import io.binarycodes.whichday.people.domain.Person;

/**
 * Turning an address into somebody. A poll stores addresses and needs names to show,
 * and it has no business with the rest of the directory — no remembering anybody, and
 * certainly no searching — so this is the part of it a poll is handed.
 */
public interface PersonLookup {

    /** The account behind an address, or somebody new who is reachable at it anyway. */
    Person forInvite(String email);

    /**
     * The same answer for many addresses at once, which is what keeps a screenful of
     * polls from reading the account table once per person. Every address asked about
     * appears in the result, so a caller never has to handle a missing one.
     */
    Map<String, Person> forInvites(Collection<String> emails);
}
