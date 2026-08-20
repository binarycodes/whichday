package io.binarycodes.findadate.poll.ui.view;

import java.util.Optional;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.checkbox.Checkbox;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.router.Route;

import io.binarycodes.findadate.base.ui.Chip;
import io.binarycodes.findadate.base.ui.Counts;
import io.binarycodes.findadate.base.ui.DateText;
import io.binarycodes.findadate.base.ui.HintBar;
import io.binarycodes.findadate.base.ui.LiveBadge;
import io.binarycodes.findadate.base.ui.Typography;
import io.binarycodes.findadate.poll.domain.Ballot;
import io.binarycodes.findadate.poll.domain.Poll;
import io.binarycodes.findadate.poll.ui.component.DayPoster;
import io.binarycodes.findadate.poll.ui.component.TallyList;
import io.binarycodes.findadate.poll.ui.presenter.PollPresenter;

/**
 * What you picked, at poster scale, with the standings underneath — a receipt
 * rather than a thank-you page, so that coming back to it is worth doing.
 */
@Route("vote/:slug/done")
public class ReceiptView extends PollScreen {

    /** The standings are context here, not the subject: the top few are enough. */
    private static final int VISIBLE_TALLIES = 3;
    private static final int VISIBLE_POSTERS = 3;

    public ReceiptView(PollPresenter presenter) {
        super(presenter);
    }

    @Override
    protected void build(Poll poll) {
        var ballot = poll.ballotOf(presenter.viewer());

        var badge = new Chip(getTranslation("receipt.badge"), Chip.Tone.LIVE);
        var edit = new Button(getTranslation("receipt.edit"), ignored -> goTo(BallotView.class));
        edit.addClassName("action-link");
        var header = new Div(badge, edit);
        header.addClassName("row-between");
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
        if (poll.closesAt() != null) {
            var closes = Typography.meta(getTranslation("receipt.closes",
                    DateText.closing(this, poll.closesAt())));
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
        ballot.chosenDays().stream().sorted().limit(VISIBLE_POSTERS)
                .forEach(day -> row.add(new DayPoster(day)));
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
     * No caption on the bars here: the receipt is about your own answer, and the
     * standings are context underneath it.
     */
    private TallyList standings(Poll poll) {
        var visible = poll.tallies().stream().limit(VISIBLE_TALLIES).toList();
        var list = new TallyList(visible, ignored -> Optional.<String>empty()).compact();
        list.addClassName("push-l");
        return list;
    }

    @Override
    public String getPageTitle() {
        return getTranslation("receipt.title");
    }
}
