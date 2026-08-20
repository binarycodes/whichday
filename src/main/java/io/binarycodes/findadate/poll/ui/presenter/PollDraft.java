package io.binarycodes.findadate.poll.ui.presenter;

import java.util.ArrayList;
import java.util.List;

import io.binarycodes.findadate.people.domain.Person;

/**
 * A poll being put together, held for as long as the session is. It exists so that
 * naming a poll and choosing who decides it can be two screens without either
 * writing a half-built poll into the store — an abandoned draft should leave nothing
 * behind.
 */
public class PollDraft {

    private final List<Person> invitees = new ArrayList<>();

    private String title = "";

    public String title() {
        return title;
    }

    public void rename(String newTitle) {
        this.title = newTitle == null ? "" : newTitle;
    }

    public List<Person> invitees() {
        return List.copyOf(invitees);
    }

    public boolean isEmpty() {
        return invitees.isEmpty();
    }

    public boolean contains(String email) {
        return invitees.stream().anyMatch(invitee -> invitee.email().equals(email));
    }

    /** Adding the same address twice would double every denominator it appears in. */
    public void invite(Person person) {
        if (!contains(person.email())) {
            invitees.add(person);
        }
    }

    public void uninvite(Person person) {
        invitees.removeIf(invitee -> invitee.email().equals(person.email()));
    }

    public void clear() {
        invitees.clear();
    }

    public void reset() {
        title = "";
        invitees.clear();
    }
}
