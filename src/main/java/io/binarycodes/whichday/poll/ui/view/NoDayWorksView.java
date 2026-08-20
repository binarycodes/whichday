package io.binarycodes.whichday.poll.ui.view;

import java.time.LocalDate;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.NativeButton;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.textfield.TextArea;
import com.vaadin.flow.data.value.ValueChangeMode;
import com.vaadin.flow.router.Route;

import io.binarycodes.whichday.base.ui.Actions;
import io.binarycodes.whichday.base.ui.Counts;
import io.binarycodes.whichday.base.ui.HintBar;
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
@Route("vote/:slug/none")
public class NoDayWorksView extends PollScreen {

    private final Set<LocalDate> proposed = new LinkedHashSet<>();
    private final Div posters = new Div();
    private final Div picker = new Div();
    private final TextArea note = new TextArea();

    private NativeButton toggle;

    public NoDayWorksView(PollPresenter presenter) {
        super(presenter);
    }

    @Override
    protected void build(Poll poll) {
        proposed.clear();

        body(new TopBar(poll.title())
                .withBack(getTranslation("nav.back"), () -> goTo(BallotView.class))
                .withHome(getTranslation("nav.home"), this::goHome));

        var headline = Typography.displaySmall(getTranslation("none.headline",
                Counts.days(this, poll.candidateDays().size())));
        headline.addClassName("push-xl");
        var lede = Typography.body(getTranslation("none.lede", poll.organizer().firstName()));
        lede.addClassName("push-s");
        body(headline, lede);

        body(confirmation(), proposalSection(poll), noteField());

        var warning = new HintBar(VaadinIcon.WARNING,
                getTranslation("none.warning", poll.organizer().firstName()));
        footer(warning, Actions.commit(getTranslation("none.send"), ignored -> send()));
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
     * away until asked for. A full month is too tall to sit above the note field
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
        calendar.addValueChangeListener(event -> {
            proposed.clear();
            proposed.addAll(event.getValue());
            renderPosters();
        });
        picker.add(calendar);
        showPicker(false);

        renderPosters();
        var section = new Div(Typography.sectionLabel(getTranslation("none.propose")), posters, picker);
        section.addClassName("push-xl");
        return section;
    }

    private void renderPosters() {
        posters.removeAll();
        proposed.stream().sorted().forEach(day -> posters.add(new DayPoster(day).outlined().small()));

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

    private Div noteField() {
        note.setPlaceholder(getTranslation("none.note.placeholder"));
        note.setValueChangeMode(ValueChangeMode.LAZY);
        note.setWidthFull();
        note.addClassName("field-emphasis");
        var group = new Div(Typography.fieldLabel(getTranslation("none.note")), note);
        group.addClassNames("stack-xs", "push-xl");
        return group;
    }

    private void send() {
        presenter.declineAll(slug(), List.copyOf(proposed), note.getValue());
        Notification.show(getTranslation("none.sent"));
        goTo(ReceiptView.class);
    }

    @Override
    public String getPageTitle() {
        return getTranslation("none.title");
    }
}
