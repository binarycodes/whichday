package io.binarycodes.whichday.poll.ui.view;

import jakarta.annotation.security.PermitAll;

import java.time.LocalDate;
import java.util.LinkedHashSet;
import java.util.Set;

import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.Route;

import io.binarycodes.whichday.base.ui.Actions;
import io.binarycodes.whichday.base.ui.ColorSchemeChoice;
import io.binarycodes.whichday.base.ui.Toast;
import io.binarycodes.whichday.base.ui.Typography;
import io.binarycodes.whichday.people.ui.AccountLabels;
import io.binarycodes.whichday.people.ui.AccountMenu;
import io.binarycodes.whichday.poll.domain.DayTally;
import io.binarycodes.whichday.poll.domain.Poll;
import io.binarycodes.whichday.poll.ui.component.DayBallot;
import io.binarycodes.whichday.poll.ui.presenter.PollPresenter;

/**
 * A voter taps every day that works. Multi-select rather than one choice: the whole
 * point is to give the team room to land on one, and a single pick would make that
 * impossible to express.
 */
@PermitAll
@Route("vote/:id")
public class BallotView extends PollScreen {

    private final Set<LocalDate> chosen = new LinkedHashSet<>();
    private final Div progress = new Div();

    private final ColorSchemeChoice scheme;

    public BallotView(PollPresenter presenter, ColorSchemeChoice scheme) {
        super(presenter);
        this.scheme = scheme;
    }

    /** Voting is over, so there is nothing to answer — the standings are the story now. */
    @Override
    protected boolean redirect(BeforeEnterEvent event, Poll poll) {
        if (poll.isOpen()) {
            return false;
        }
        forwardToPoll(event, poll.ballotOf(presenter.viewer()).isPresent()
                ? ReceiptView.class
                : ResultsView.class);
        return true;
    }

    @Override
    protected void build(Poll poll) {
        chosen.clear();
        poll.ballotOf(presenter.viewer()).ifPresent(ballot -> chosen.addAll(ballot.chosenDays()));

        body(invitation(poll));

        var headline = Typography.displaySmall(getTranslation("ballot.headline"));
        headline.addClassName("push-xl");
        var lede = Typography.body(getTranslation("ballot.lede"));
        lede.addClassName("push-s");
        body(headline, lede);

        var ballot = new DayBallot(presenter.today());
        if (presenter.anonymous()) {
            ballot.withVoterNames();
        }
        ballot.addClassNames("ballot-field", "push-xl");
        ballot.setNoteText(this::noteFor);
        ballot.setTallies(poll.tallies());
        ballot.setValue(Set.copyOf(chosen));
        ballot.addValueChangeListener(event -> {
            chosen.clear();
            chosen.addAll(event.getValue());
            renderProgress(poll);
        });
        body(ballot);

        progress.addClassNames("row-between", "meta");
        footer(progress, Actions.primary(getTranslation("ballot.submit"), ignored -> submit(poll)));
        renderProgress(poll);
    }

    /**
     * Who invited you, between the way home and the account — which stays hard right
     * here as it does on every other screen that shows it.
     */
    private Div invitation(Poll poll) {
        var text = Typography.meta(getTranslation("ballot.invitedBy",
                poll.organizer().firstName(), poll.title()));
        var account = new AccountMenu(presenter.viewer(),
                AccountLabels.of(this, presenter.viewer(), presenter.anonymous()),
                scheme, presenter::signOut);
        var row = new Div(homeButton(), text, account);
        row.addClassName("invitation");
        return row;
    }

    /**
     * What a row says about the votes already on it. The day in front always gets
     * words, because "most popular" is the reason to look.
     */
    private String noteFor(DayTally tally, boolean leading) {
        if (tally.day().isBefore(presenter.today())) {
            return getTranslation("ballot.past");
        }
        if (tally.voteCount() == 0) {
            return getTranslation("ballot.soFar.nobody");
        }
        var soFar = tally.voteCount() == 1
                ? getTranslation("ballot.soFar.one")
                : getTranslation("ballot.soFar.many", tally.voteCount());
        return leading ? getTranslation("ballot.soFar.leading", soFar) : soFar;
    }

    private void renderProgress(Poll poll) {
        progress.removeAll();
        progress.add(new Span(getTranslation("ballot.selected", chosen.size(), poll.candidateDays().size())));
        progress.add(Actions.link(getTranslation("ballot.noneWork"), ignored -> goTo(NoDayWorksView.class)));
    }

    private void submit(Poll poll) {
        if (chosen.isEmpty()) {
            Toast.error(getTranslation("ballot.needOne"));
            return;
        }
        presenter.vote(id(), Set.copyOf(chosen));
        Toast.success(getTranslation("ballot.submitted"));
        // The organizer came from the standings and wants them back; everybody else
        // wants the receipt for the answer they just gave.
        goTo(presenter.isOrganizer(poll) ? ResultsView.class : ReceiptView.class);
    }

    @Override
    public String getPageTitle() {
        return getTranslation("ballot.title");
    }
}
