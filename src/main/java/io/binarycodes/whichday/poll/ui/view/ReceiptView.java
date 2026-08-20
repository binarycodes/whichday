package io.binarycodes.whichday.poll.ui.view;

import jakarta.annotation.security.PermitAll;

import java.util.Optional;

import com.vaadin.flow.component.checkbox.Checkbox;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.router.Route;

import io.binarycodes.whichday.base.ui.Actions;
import io.binarycodes.whichday.base.ui.Chip;
import io.binarycodes.whichday.base.ui.Counts;
import io.binarycodes.whichday.base.ui.DateText;
import io.binarycodes.whichday.base.ui.HintBar;
import io.binarycodes.whichday.base.ui.LiveBadge;
import io.binarycodes.whichday.base.ui.Typography;
import io.binarycodes.whichday.poll.domain.Ballot;
import io.binarycodes.whichday.poll.domain.Poll;
import io.binarycodes.whichday.poll.ui.component.DayPoster;
import io.binarycodes.whichday.poll.ui.component.TallyList;
import io.binarycodes.whichday.poll.ui.presenter.PollPresenter;

/**
 * What you picked, at poster scale, with the standings underneath — a receipt
 * rather than a thank-you page, so that coming back to it is worth doing.
 */
@PermitAll
@Route("vote/:slug/done")
public class ReceiptView extends PollScreen {

    public ReceiptView(PollPresenter presenter) {
        super(presenter);
    }

    @Override
    protected void build(Poll poll) {
        var ballot = poll.ballotOf(presenter.viewer());

        var badge = new Chip(getTranslation("receipt.badge"), Chip.Tone.LIVE);
        var edit = Actions.link(getTranslation("receipt.edit"), ignored -> goTo(BallotView.class));
        var header = new Div(homeButton(), badge, edit);
        header.addClassNames("row-between", "receipt-header");
        body(header);

        var summary = Typography.fieldLabel(summaryTextFor(ballot));
        summary.addClassName("push-2xl");
        body(summary);
        ballot.filter(answer -> !answer.isDeclined()).ifPresent(answer -> body(posters(answer)));
        ballot.filter(Ballot::isDeclined).ifPresent(answer -> body(proposals(answer)));

        body(standingsHeader(poll), standings(poll));

        var notify = new HintBar(VaadinIcon.BELL, getTranslation("receipt.notify")).outlined()
                .withAction(new Checkbox(true));
        footer(notify);
        if (poll.closesOn() != null) {
            var closes = Typography.meta(getTranslation(
                    poll.isClosed() ? "receipt.closed" : "receipt.closes",
                    DateText.closing(this, poll.closesOn())));
            closes.addClassNames("meta-faint", "meta-centred");
            footer(closes);
        }
    }

    private String summaryTextFor(Optional<Ballot> ballot) {
        return ballot.map(answer -> {
            if (answer.isDeclined()) {
                return getTranslation("receipt.declined");
            }
            return answer.chosenDays().size() == 1
                    ? getTranslation("receipt.saidYes.one")
                    : getTranslation("receipt.saidYes.many", answer.chosenDays().size());
        }).orElseGet(() -> getTranslation("ballot.needOne"));
    }

    private Div posters(Ballot ballot) {
        var row = new Div();
        row.addClassNames("poster-row", "push-m");
        ballot.chosenDays().stream().sorted().forEach(day -> row.add(new DayPoster(day)));
        return row;
    }

    /** The days you put forward instead, if that is the answer you gave. */
    private Div proposals(Ballot ballot) {
        var row = new Div();
        row.addClassNames("poster-row", "push-m");
        ballot.proposedDays().stream().sorted().forEach(day -> row.add(new DayPoster(day).outlined().small()));
        return row;
    }

    private Div standingsHeader(Poll poll) {
        var title = Typography.fieldLabel(getTranslation("receipt.standings"));
        var progress = new LiveBadge(Counts.progress(this, poll.answerCount(), poll.inviteCount()));
        var header = new Div(title, progress);
        header.addClassNames("row-between", "divider-bottom", "push-3xl");
        return header;
    }

    /**
     * Every day on the table, not the leading few. A voter who said yes to five days
     * and is shown three bars cannot tell whether the other two are losing or simply
     * not drawn — and "where the team stands" promises the whole poll.
     *
     * <p>No caption on the bars: the posters above already say which days are yours.
     */
    private TallyList standings(Poll poll) {
        var list = new TallyList(poll.tallies(), ignored -> Optional.<String>empty()).compact();
        list.addClassName("push-l");
        return list;
    }

    @Override
    public String getPageTitle() {
        return getTranslation("receipt.title");
    }
}
