package io.binarycodes.findadate.poll.ui.view;

import java.time.LocalDate;
import java.util.LinkedHashSet;
import java.util.Set;

import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.router.Route;

import io.binarycodes.findadate.base.ui.Actions;
import io.binarycodes.findadate.base.ui.Chip;
import io.binarycodes.findadate.base.ui.Counts;
import io.binarycodes.findadate.base.ui.DateText;
import io.binarycodes.findadate.base.ui.Typography;
import io.binarycodes.findadate.base.ui.TopBar;
import io.binarycodes.findadate.poll.domain.Poll;
import io.binarycodes.findadate.poll.ui.component.MonthCalendar;
import io.binarycodes.findadate.poll.ui.presenter.PollPresenter;

/**
 * The organizer puts days on the table. The calendar is the hero here, so the
 * running total and the chips for what is chosen live in the footer where they
 * cannot push it around.
 */
@Route("poll/:slug/days")
public class CandidateDaysView extends PollScreen {

    private final Set<LocalDate> chosen = new LinkedHashSet<>();
    private final Div chips = new Div();
    private final Div summary = new Div();

    public CandidateDaysView(PollPresenter presenter) {
        super(presenter);
    }

    @Override
    protected void build(Poll poll) {
        chosen.clear();
        chosen.addAll(poll.candidateDays());

        body(new TopBar(poll.title()).withBack(getTranslation("nav.back"), this::goBack).withTrailingSpace());

        var calendar = new MonthCalendar(presenter.today(),
                getTranslation("days.previousMonth"),
                getTranslation("days.nextMonth"));
        calendar.addClassNames("calendar-field", "push-l");
        calendar.setValue(Set.copyOf(chosen));
        calendar.addValueChangeListener(event -> {
            chosen.clear();
            chosen.addAll(event.getValue());
            renderChosen();
        });
        body(calendar);

        summary.addClassNames("row-between", "divider-top");
        chips.addClassNames("chip-row", "push-m");
        footer(summary, chips, Actions.commit(getTranslation("days.send"), ignored -> send()));
        renderChosen();
    }

    private void renderChosen() {
        summary.removeAll();
        summary.add(Typography.fieldLabel(chosen.isEmpty()
                ? getTranslation("days.none")
                : getTranslation("days.onTheTable", Counts.days(this, chosen.size()))));
        if (!chosen.isEmpty()) {
            summary.add(Actions.link(getTranslation("days.clear"), ignored -> clear()));
        }

        chips.removeAll();
        chosen.stream().sorted().forEach(day ->
                chips.add(new Chip(DateText.compact(this, day), Chip.Tone.ACCENT)));
    }

    private void clear() {
        presenter.chooseDays(slug(), Set.of());
        render();
    }

    private void send() {
        if (chosen.isEmpty()) {
            Notification.show(getTranslation("days.needOne"));
            return;
        }
        presenter.chooseDays(slug(), Set.copyOf(chosen));
        goTo(ShareView.class);
    }

    @Override
    public String getPageTitle() {
        return getTranslation("days.title");
    }
}
