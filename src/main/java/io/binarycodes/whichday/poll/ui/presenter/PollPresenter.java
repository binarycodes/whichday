package io.binarycodes.whichday.poll.ui.presenter;

import java.time.Clock;
import java.util.ArrayList;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

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

    public void signOut() {
        session.signOut();
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

    /** The viewer's own unsent polls. */
    public List<PollSummary> draftPolls() {
        return polls.draftPolls(viewer());
    }

    public void deleteDraft(UUID id) {
        polls.deleteDraft(id, viewer());
    }

    public List<PollSummary> settledPolls() {
        return polls.settledPolls(viewer());
    }

    /** Polls the viewer has not answered yet — what the list screen's headline counts. */
    public long awaitingViewer() {
        return openPolls().stream().filter(PollSummary::needsViewer).count();
    }

    /**
     * The poll, if it is the viewer's to see. Every screen goes through here, so this
     * is where the viewer gets attached to the question — a poll somebody was not
     * invited to is absent rather than forbidden.
     */
    public Optional<Poll> poll(UUID id) {
        return polls.poll(id, viewer());
    }

    /** The poll being put together. Survives the trip to the invitee screen and back. */
    public PollDraft draft() {
        return draft;
    }

    public List<AccountMatch> searchInvitees(String query) {
        return invitees.matching(query, viewer(), draft.invitees());
    }

    /** Whether the query was reaching for the viewer's own account. */
    public boolean searchMatchesViewer(String query) {
        return invitees.matchesSearcher(query, viewer());
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
    public UUID createFromDraft() {
        var everybody = new ArrayList<Person>();
        everybody.add(viewer());
        draft.invitees().stream()
                .filter(invitee -> !invitee.email().equals(viewer().email()))
                .forEach(everybody::add);
        var id = polls.create(draft.title(), viewer(), everybody);
        draft.reset();
        return id;
    }

    /** The closing date a poll has, or the one it would get if sent now. */
    public Optional<LocalDate> plannedClosing(UUID id) {
        return polls.plannedClosing(id);
    }

    /** The last date the organizer may close on: the day before the first day on the table. */
    public Optional<LocalDate> latestClosingDay(UUID id) {
        return polls.latestClosingDay(id);
    }

    public void closeOn(UUID id, LocalDate day) {
        polls.closeOn(id, viewer(), day);
    }

    public void allowAlternatives(UUID id, boolean allowed) {
        polls.allowAlternatives(id, viewer(), allowed);
    }

    public void addInvitee(UUID id, Person person) {
        polls.addInvitee(id, viewer(), person);
    }

    public void chooseDays(UUID id, Set<LocalDate> days) {
        polls.replaceCandidateDays(id, viewer(), days);
    }

    public void send(UUID id) {
        polls.send(id, viewer());
    }

    public void vote(UUID id, Set<LocalDate> days) {
        polls.castVote(id, viewer(), days);
    }

    public void declineAll(UUID id, List<LocalDate> proposedDays, String note) {
        polls.decline(id, viewer(), proposedDays, note);
    }

    public void acceptProposal(UUID id, LocalDate day) {
        polls.acceptProposal(id, viewer(), day);
    }

    public void lock(UUID id, LocalDate day) {
        polls.lock(id, viewer(), day);
    }

    public Optional<Ballot> ballotOf(UUID id) {
        return poll(id).flatMap(poll -> poll.ballotOf(viewer()));
    }

    public boolean isOrganizer(Poll poll) {
        return poll.organizer().equals(viewer());
    }
}
