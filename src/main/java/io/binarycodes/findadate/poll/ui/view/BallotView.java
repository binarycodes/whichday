package io.binarycodes.findadate.poll.ui.view;

import java.time.LocalDate;
import java.util.LinkedHashSet;
import java.util.Set;

import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.router.Route;

import io.binarycodes.findadate.base.ui.Actions;
import io.binarycodes.findadate.base.ui.Typography;
import io.binarycodes.findadate.people.ui.PersonAvatar;
import io.binarycodes.findadate.poll.domain.DayTally;
import io.binarycodes.findadate.poll.domain.Poll;
import io.binarycodes.findadate.poll.ui.component.DayBallot;
import io.binarycodes.findadate.poll.ui.presenter.PollPresenter;

/**
 * A voter taps every day that works. Multi-select rather than one choice: the whole
 * point is to give the team room to land on one, and a single pick would make that
 * impossible to express.
 */
@Route("vote/:slug")
public class BallotView extends PollScreen {

    private final Set<LocalDate> chosen = new LinkedHashSet<>();
    private final Div progress = new Div();

    public BallotView(PollPresenter presenter) {
        super(presenter);
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

    private Div invitation(Poll poll) {
        var text = Typography.meta(getTranslation("ballot.invitedBy",
                poll.organizer().firstName(), poll.title()));
        var row = new Div(homeButton(), new PersonAvatar(presenter.viewer()), text);
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
            Notification.show(getTranslation("ballot.needOne"));
            return;
        }
        presenter.vote(slug(), Set.copyOf(chosen));
        Notification.show(getTranslation("ballot.submitted"));
        goTo(ReceiptView.class);
    }

    @Override
    public String getPageTitle() {
        return getTranslation("ballot.title");
    }
}
