package io.binarycodes.findadate.poll.ui.presenter;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.springframework.stereotype.Component;

import com.vaadin.flow.spring.annotation.VaadinSessionScope;

import io.binarycodes.findadate.people.domain.Person;
import io.binarycodes.findadate.people.ui.presenter.ViewerSession;
import io.binarycodes.findadate.poll.domain.Ballot;
import io.binarycodes.findadate.poll.domain.Poll;
import io.binarycodes.findadate.poll.domain.PollSummary;
import io.binarycodes.findadate.poll.service.PollService;

/**
 * What the screens talk to. It pairs the shared store with the one thing that is
 * per-session — who is looking — so that no view has to pass a viewer down into
 * every call, and so that the service stays free of session state.
 */
@Component
@VaadinSessionScope
public class PollPresenter {

    private final PollService polls;
    private final ViewerSession session;
    private final Clock clock;

    public PollPresenter(PollService polls, ViewerSession session, Clock clock) {
        this.polls = polls;
        this.session = session;
        this.clock = clock;
    }

    public Person viewer() {
        return session.viewer();
    }

    public List<Person> everyone() {
        return session.everyone();
    }

    public String teamName() {
        return session.teamName();
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

    public String create(String title) {
        return polls.create(title, viewer(), session.everyone());
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
