package io.binarycodes.whichday.poll.ui.view;

import jakarta.annotation.security.PermitAll;

import java.time.LocalDate;
import java.util.List;

import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.Route;

import io.binarycodes.whichday.base.ui.Actions;
import io.binarycodes.whichday.base.ui.DateText;
import io.binarycodes.whichday.base.ui.HintBar;
import io.binarycodes.whichday.base.ui.Toast;
import io.binarycodes.whichday.base.ui.TopBar;
import io.binarycodes.whichday.base.ui.Typography;
import io.binarycodes.whichday.poll.domain.DayTally;
import io.binarycodes.whichday.poll.domain.Poll;
import io.binarycodes.whichday.poll.ui.component.DayChoice;
import io.binarycodes.whichday.poll.ui.presenter.PollPresenter;

/**
 * The last thing between a poll and a decision. Locking a day is final — no more
 * answers, no different day, nothing to undo — so it gets a screen of its own to say
 * so, rather than happening under the organizer's thumb on the standings.
 *
 * <p>It is also where a tie is settled. The standings can say three days are level;
 * only somebody can say which one the team goes with, and this is where they say it.
 */
@PermitAll
@Route("poll/:id/settle")
public class SettleView extends PollScreen {

    private LocalDate settled;

    public SettleView(PollPresenter presenter) {
        super(presenter);
    }

    /**
     * The organizer's, and only while the poll is still open — the same rule the
     * standings apply to the button that leads here. Anybody else, and everybody once
     * voting is over, is sent to the screen the poll actually has for them.
     */
    @Override
    protected boolean redirect(BeforeEnterEvent event, Poll poll) {
        if (presenter.isOrganizer(poll) && poll.isOpen() && hasSomethingToSettle(poll)) {
            return false;
        }
        forwardToPoll(event, ResultsView.class);
        return true;
    }

    /** Nobody has voted, so there is no day in front and nothing to choose between. */
    private boolean hasSomethingToSettle(Poll poll) {
        return poll.leader().isPresent() || !poll.tiedAtTheTop().isEmpty();
    }

    @Override
    protected void build(Poll poll) {
        body(new TopBar(getTranslation("settle.title"))
                .withBack(getTranslation("nav.back"), () -> goTo(ResultsView.class))
                .withTrailingSpace());

        var tied = poll.tiedAtTheTop();
        settled = poll.leader().map(DayTally::day).orElse(null);

        var headline = Typography.displayMedium(settled == null
                ? getTranslation("settle.headline.tied")
                : getTranslation("settle.headline.one", DateText.full(this, settled)));
        headline.addClassName("push-l");
        body(headline);

        if (settled == null) {
            var lede = Typography.lede(getTranslation("settle.lede.tied", tied.size()));
            lede.addClassName("push-xl");
            body(lede, dayChoice(tied));
        }

        footer(new HintBar(VaadinIcon.LOCK, getTranslation("settle.warning")).outlined(),
                Actions.commit(getTranslation("settle.confirm"), ignored -> settle()),
                Actions.outline(getTranslation("settle.cancel"), ignored -> goTo(ResultsView.class)));
    }

    private Div dayChoice(List<DayTally> tied) {
        var picker = new DayChoice(tied.stream().map(DayTally::day).toList());
        picker.addClassName("day-choice");
        picker.addValueChangeListener(event -> settled = event.getValue());
        var wrapper = new Div(picker);
        wrapper.addClassName("push-xl");
        return wrapper;
    }

    private void settle() {
        if (settled == null) {
            Toast.error(getTranslation("settle.needOne"));
            return;
        }
        presenter.lock(id(), settled);
        goTo(LockedView.class);
    }

    @Override
    public String getPageTitle() {
        return getTranslation("settle.title");
    }
}
