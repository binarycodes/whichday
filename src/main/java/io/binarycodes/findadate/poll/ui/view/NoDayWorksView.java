package io.binarycodes.findadate.poll.ui.view;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import com.vaadin.flow.component.datepicker.DatePicker;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.NativeButton;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.textfield.TextArea;
import com.vaadin.flow.data.value.ValueChangeMode;
import com.vaadin.flow.router.Route;

import io.binarycodes.findadate.base.ui.Actions;
import io.binarycodes.findadate.base.ui.Counts;
import io.binarycodes.findadate.base.ui.HintBar;
import io.binarycodes.findadate.base.ui.TopBar;
import io.binarycodes.findadate.base.ui.Typography;
import io.binarycodes.findadate.poll.domain.Poll;
import io.binarycodes.findadate.poll.ui.component.DayPoster;
import io.binarycodes.findadate.poll.ui.presenter.PollPresenter;

/**
 * A counter-proposal instead of a dead end. Saying no is an answer — it is what
 * separates a voter who cannot make it from one who has not replied — and the days
 * put forward stay a suggestion until the organizer accepts one.
 */
@Route("vote/:slug/none")
public class NoDayWorksView extends PollScreen {

    private static final int PROPOSAL_LIMIT = 3;

    private final List<LocalDate> proposed = new ArrayList<>();
    private final Div posters = new Div();
    private final TextArea note = new TextArea();

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

        body(confirmation(), proposalSection(), noteField());

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

    private Div proposalSection() {
        posters.addClassNames("poster-row", "push-m");
        renderPosters();
        var section = new Div(Typography.sectionLabel(getTranslation("none.propose")), posters);
        section.addClassName("push-xl");
        return section;
    }

    private void renderPosters() {
        posters.removeAll();
        proposed.stream().sorted().forEach(day -> posters.add(new DayPoster(day).outlined().small()));
        if (proposed.size() < PROPOSAL_LIMIT) {
            var add = new NativeButton();
            add.add(new Icon(VaadinIcon.PLUS));
            add.addClassName("poster-add");
            add.setAriaLabel(getTranslation("none.addDay"));
            add.addClickListener(ignored -> askForDay());
            posters.add(add);
        }
    }

    private void askForDay() {
        var picker = new DatePicker(getTranslation("none.addDay"));
        picker.setMin(presenter.today());
        var dialog = new Dialog(picker);
        dialog.getFooter().add(Actions.primary(getTranslation("none.addDay"), ignored -> {
            if (picker.getValue() != null && !proposed.contains(picker.getValue())) {
                proposed.add(picker.getValue());
                renderPosters();
            }
            dialog.close();
        }));
        dialog.open();
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
