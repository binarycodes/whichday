package io.binarycodes.whichday.poll.ui.presenter;

import java.time.Clock;
import java.util.ArrayList;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.springframework.stereotype.Component;

import com.vaadin.flow.spring.annotation.VaadinSessionScope;

import io.binarycodes.whichday.people.domain.Person;
import io.binarycodes.whichday.people.ui.presenter.ViewerSession;
import io.binarycodes.whichday.poll.domain.Ballot;
import io.binarycodes.whichday.poll.domain.Poll;
import io.binarycodes.whichday.poll.domain.PollSummary;
import io.binarycodes.whichday.poll.domain.AccountMatch;
import io.binarycodes.whichday.poll.service.InviteeSearch;
import io.binarycodes.whichday.poll.service.PollService;

/**
 * What the screens talk to. It pairs the shared store with the one thing that is
 * per-session — who is looking — so that no view has to pass a viewer down into
 * every call, and so that the service stays free of session state.
 */
@Component
@VaadinSessionScope
public class PollPresenter {

    private final PollService polls;
    private final InviteeSearch invitees;
    private final ViewerSession session;
    private final Clock clock;
    private final PollDraft draft = new PollDraft();

    public PollPresenter(PollService polls, InviteeSearch invitees, ViewerSession session, Clock clock) {
        this.polls = polls;
        this.invitees = invitees;
        this.session = session;
        this.clock = clock;
    }

    public Person viewer() {
        return session.viewer();
    }

    /** Only the account switcher, which stands in for a login. */
    public List<Person> everyone() {
        return session.everyone();
    }

    public void switchViewer(Person person) {
        session.switchTo(person);
    }

    public LocalDate today() {
        return LocalDate.now(clock);
    }

    public LocalDateTime now() {
        return LocalDateTime.now(clock);
    }

    public Instant instant() {
        return clock.instant();
    }

    public List<PollSummary> openPolls() {
        return polls.openPolls(viewer());
    }

    public List<PollSummary> settledPolls() {
        return polls.settledPolls(viewer());
    }

    /** Polls the viewer has not answered yet — what the list screen's headline counts. */
    public long awaitingViewer() {
        return openPolls().stream().filter(summary -> !summary.answeredByViewer()).count();
    }

    public Optional<Poll> poll(String slug) {
        return polls.poll(slug);
    }

    /** The poll being put together. Survives the trip to the invitee screen and back. */
    public PollDraft draft() {
        return draft;
    }

    public List<AccountMatch> searchInvitees(String query) {
        return invitees.matching(query, viewer(), draft.invitees());
    }

    /** The address itself, whether or not an account answers to it. */
    public Person inviteeFor(String email) {
        return invitees.inviteFor(email);
    }

    public boolean hasAccount(String email) {
        return invitees.hasAccount(email);
    }

    /**
     * The organizer leads the invited list, and the rest keep the order they were
     * added in — not directory order, which an outsider has no place in. Anybody who
     * managed to add the organizer to their own draft is not counted twice.
     */
    public String createFromDraft() {
        var everybody = new ArrayList<Person>();
        everybody.add(viewer());
        draft.invitees().stream()
                .filter(invitee -> !invitee.email().equals(viewer().email()))
                .forEach(everybody::add);
        var slug = polls.create(draft.title(), viewer(), everybody);
        draft.reset();
        return slug;
    }

    public void addInvitee(String slug, Person person) {
        polls.addInvitee(slug, person);
    }

    public void chooseDays(String slug, Set<LocalDate> days) {
        polls.replaceCandidateDays(slug, days);
    }

    public void send(String slug) {
        polls.send(slug);
    }

    public void vote(String slug, Set<LocalDate> days) {
        polls.castVote(slug, viewer(), days);
    }

    public void declineAll(String slug, List<LocalDate> proposedDays, String note) {
        polls.decline(slug, viewer(), proposedDays, note);
    }

    public void acceptProposal(String slug, LocalDate day) {
        polls.acceptProposal(slug, day);
    }

    public void lock(String slug, LocalDate day) {
        polls.lock(slug, day);
    }

    public Optional<Ballot> ballotOf(String slug) {
        return poll(slug).flatMap(poll -> poll.ballotOf(viewer()));
    }

    public boolean isOrganizer(Poll poll) {
        return poll.organizer().equals(viewer());
    }
}
