package io.binarycodes.whichday.poll.ui.view;

import jakarta.annotation.security.PermitAll;

import java.time.LocalDate;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.NativeButton;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.data.value.ValueChangeMode;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.Route;

import io.binarycodes.whichday.base.ui.Actions;
import io.binarycodes.whichday.base.ui.Counts;
import io.binarycodes.whichday.base.ui.HintBar;
import io.binarycodes.whichday.base.ui.Home;
import io.binarycodes.whichday.base.ui.Toast;
import io.binarycodes.whichday.base.ui.TopBar;
import io.binarycodes.whichday.base.ui.Typography;
import io.binarycodes.whichday.poll.domain.Poll;
import io.binarycodes.whichday.poll.ui.component.DayPoster;
import io.binarycodes.whichday.poll.ui.component.MonthCalendar;
import io.binarycodes.whichday.poll.ui.presenter.PollPresenter;

/**
 * A counter-proposal instead of a dead end. Saying no is an answer — it is what
 * separates a voter who cannot make it from one who has not replied — and the days
 * put forward stay a suggestion until the organizer accepts one.
 */
@PermitAll
@Route("vote/:id/none")
public class NoDayWorksView extends PollScreen {

    /**
     * Three is what the poster row holds across a phone, and a counter-proposal with
     * more alternatives than the poll it is answering stops being one.
     */
    private static final int PROPOSAL_LIMIT = 3;

    private final Set<LocalDate> proposed = new LinkedHashSet<>();
    private final Div posters = new Div();
    private final Div picker = new Div();

    private NativeButton toggle;

    public NoDayWorksView(PollPresenter presenter) {
        super(presenter);
    }

    @Override
    protected boolean redirect(BeforeEnterEvent event, Poll poll) {
        if (poll.isOpen()) {
            return false;
        }
        forwardToPoll(event, ResultsView.class);
        return true;
    }

    @Override
    protected void build(Poll poll) {
        proposed.clear();

        body(new TopBar(poll.title())
                .withBack(getTranslation("nav.back"), () -> goTo(BallotView.class))
                .withHome(Home.labelFor(this, presenter), this::goHome));

        var headline = Typography.displaySmall(getTranslation("none.headline",
                Counts.days(this, poll.candidateDays().size())));
        headline.addClassName("push-xl");
        var lede = Typography.body(getTranslation("none.lede", poll.organizer().firstName()));
        lede.addClassName("push-s");
        body(headline, lede);

        body(confirmation());
        if (poll.alternativesAllowed()) {
            body(proposalSection(poll));
        }

        // With alternatives off there is no proposal to caveat, so the footnote says what
        // is actually true of this poll instead.
        var footnote = poll.alternativesAllowed()
                ? new HintBar(VaadinIcon.WARNING, getTranslation("none.warning", poll.organizer().firstName()))
                : new HintBar(VaadinIcon.INFO_CIRCLE,
                        getTranslation("none.alternativesOff", poll.organizer().firstName()));
        footer(footnote, Actions.commit(getTranslation("none.send"), ignored -> send()));
    }

    /** Not a control: it states the answer that arriving on this screen already gave. */
    private Div confirmation() {
        var mark = new Span(new Icon(VaadinIcon.CHECK));
        mark.addClassName("confirmation-mark");
        var row = new Div(mark, new Span(getTranslation("none.confirm")));
        row.addClassNames("confirmation", "push-xl");
        return row;
    }

    /**
     * The days chosen so far, and the calendar that chooses them — inline, and folded
     * away until asked for. A full month is too tall to leave open on this screen
     * permanently, and an overlay on a phone would cover the very posters it is
     * filling in.
     */
    private Div proposalSection(Poll poll) {
        posters.addClassNames("poster-row", "push-m");
        picker.addClassNames("proposal-picker", "push-m");

        var calendar = new MonthCalendar(presenter.today(),
                getTranslation("days.previousMonth"),
                getTranslation("days.nextMonth"));
        calendar.addClassName("calendar-field");
        // A day already on the table is not an alternative to it.
        calendar.setUnavailable(poll.candidateDays());
        calendar.setMaximumSelection(PROPOSAL_LIMIT);
        calendar.addValueChangeListener(event -> onProposalChanged(calendar, event.getValue()));

        var limit = Typography.meta(getTranslation("none.propose.limit", PROPOSAL_LIMIT));
        limit.addClassName("meta-faint");
        var done = Actions.link(getTranslation("none.propose.done"), ignored -> showPicker(false));
        var actions = new Div(limit, done);
        actions.addClassNames("row-between", "push-m");

        picker.add(calendar, actions);
        showPicker(false);

        renderPosters();
        var section = new Div(Typography.sectionLabel(getTranslation("none.propose")), posters, picker);
        section.addClassName("push-xl");
        return section;
    }

    /**
     * Picking the last day it will take is the end of the decision, so the calendar
     * folds itself away — the reader does not have to work out how to dismiss it.
     * Below the limit, "Done" is there for whoever is finished early.
     */
    private void onProposalChanged(MonthCalendar calendar, Set<LocalDate> days) {
        proposed.clear();
        proposed.addAll(days);
        renderPosters();
        if (calendar.isAtMaximumSelection()) {
            showPicker(false);
        }
    }

    private void renderPosters() {
        posters.removeAll();
        proposed.stream().sorted().forEach(day -> posters.add(new DayPoster(day).outlined().small()));

        // At the limit there is nothing left to add, so the row stops inviting it.
        if (proposed.size() >= PROPOSAL_LIMIT) {
            toggle = null;
            return;
        }
        toggle = new NativeButton();
        toggle.add(new Icon(proposed.isEmpty() ? VaadinIcon.PLUS : VaadinIcon.CALENDAR));
        toggle.addClassName("poster-add");
        toggle.setAriaLabel(getTranslation("none.addDay"));
        toggle.addClickListener(ignored -> showPicker(!isPickerOpen()));
        posters.add(toggle);
        toggle.getElement().setAttribute("aria-expanded", String.valueOf(isPickerOpen()));
    }

    private boolean isPickerOpen() {
        return picker.isVisible();
    }

    private void showPicker(boolean open) {
        picker.setVisible(open);
        if (toggle != null) {
            toggle.getElement().setAttribute("aria-expanded", String.valueOf(open));
        }
    }

    private void send() {
        // Nothing can have been proposed when the poll does not take alternatives,
        // but reading the flag here keeps that true of the write as well as the view.
        presenter.declineAll(id(),
                presenter.poll(id()).map(Poll::alternativesAllowed).orElse(false)
                        ? List.copyOf(proposed)
                        : List.of());
        Toast.success(getTranslation("none.sent"));
        goTo(ReceiptView.class);
    }

    @Override
    public String getPageTitle() {
        return getTranslation("none.title");
    }
}
