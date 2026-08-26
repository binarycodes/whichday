package io.binarycodes.whichday.poll.ui.view;

import jakarta.annotation.security.PermitAll;

import java.time.LocalDate;
import java.util.LinkedHashSet;
import java.util.Set;

import com.vaadin.flow.component.checkbox.Checkbox;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.Route;

import io.binarycodes.whichday.base.ui.Actions;
import io.binarycodes.whichday.base.ui.Chip;
import io.binarycodes.whichday.base.ui.Counts;
import io.binarycodes.whichday.base.ui.DateText;
import io.binarycodes.whichday.base.ui.HintBar;
import io.binarycodes.whichday.base.ui.Toast;
import io.binarycodes.whichday.base.ui.Typography;
import io.binarycodes.whichday.base.ui.TopBar;
import io.binarycodes.whichday.poll.domain.Poll;
import io.binarycodes.whichday.poll.ui.component.MonthCalendar;
import io.binarycodes.whichday.poll.ui.presenter.PollPresenter;

/**
 * The organizer puts days on the table. The calendar is the hero here, so the
 * running total and the chips for what is chosen live in the footer where they
 * cannot push it around.
 */
@PermitAll
@Route("poll/:id/days")
public class CandidateDaysView extends PollScreen {

    private final Set<LocalDate> chosen = new LinkedHashSet<>();
    private final Div chips = new Div();
    private final Div summary = new Div();

    public CandidateDaysView(PollPresenter presenter) {
        super(presenter);
    }

    /**
     * Editing a poll is the organizer's, and only while it is still taking answers.
     * Anybody else who follows this URL, and everybody once voting is over, is sent to
     * the screen the poll actually has for them. Not the not-found screen: an invitee
     * may see the poll, just not change it.
     */
    @Override
    protected boolean redirect(BeforeEnterEvent event, Poll poll) {
        if (presenter.isOrganizer(poll) && poll.isEditable()) {
            return false;
        }
        forwardToPoll(event, ResultsView.class);
        return true;
    }

    @Override
    protected void build(Poll poll) {
        chosen.clear();
        chosen.addAll(poll.candidateDays());

        body(new TopBar(poll.title())
                .withLeading(homeButton())
                .withTrailingSpace());

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
        footer(summary, chips, alternativesToggle(poll),
                Actions.commit(getTranslation("days.next"), ignored -> goOn()));
        renderChosen();
    }

    /**
     * Whether a voter who can make none of these may put others forward. It belongs
     * on this screen because it is a rule about these days, and the organizer is
     * looking at them.
     */
    private HintBar alternativesToggle(Poll poll) {
        var allowed = new Checkbox(poll.alternativesAllowed());
        allowed.addValueChangeListener(event -> presenter.allowAlternatives(id(), event.getValue()));
        allowed.setAriaLabel(getTranslation("days.allowAlternatives"));
        return new HintBar(VaadinIcon.CALENDAR, getTranslation("days.allowAlternatives"))
                .outlined()
                .withAction(allowed);
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
        presenter.chooseDays(id(), Set.of());
        render();
    }

    /**
     * Saves the days and opens the share screen, which is all it does — nothing is sent
     * anywhere from here in either mode, and the label says so.
     */
    private void goOn() {
        if (chosen.isEmpty()) {
            Toast.error(getTranslation("days.needOne"));
            return;
        }
        presenter.chooseDays(id(), Set.copyOf(chosen));
        goTo(ShareView.class);
    }

    @Override
    public String getPageTitle() {
        return getTranslation("days.title");
    }
}
