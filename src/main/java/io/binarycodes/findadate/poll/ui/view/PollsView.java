package io.binarycodes.findadate.poll.ui.view;

import java.util.List;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.router.HasDynamicTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.router.RouteParameters;

import io.binarycodes.findadate.base.ui.Actions;
import io.binarycodes.findadate.base.ui.AppHeader;
import io.binarycodes.findadate.base.ui.Chip;
import io.binarycodes.findadate.base.ui.Counts;
import io.binarycodes.findadate.base.ui.DateText;
import io.binarycodes.findadate.base.ui.Screen;
import io.binarycodes.findadate.base.ui.Typography;
import io.binarycodes.findadate.people.domain.Person;
import io.binarycodes.findadate.poll.domain.PollSummary;
import io.binarycodes.findadate.poll.ui.component.PollRow;
import io.binarycodes.findadate.poll.ui.presenter.PollPresenter;

/**
 * Coming back: the polls you are part of, each carrying its own date numeral, and
 * the ones already settled underneath.
 */
@Route("")
public class PollsView extends Screen implements HasDynamicTitle {

    private final PollPresenter presenter;

    public PollsView(PollPresenter presenter) {
        this.presenter = presenter;
        render();
    }

    private void render() {
        clearBody();
        clearFooter();

        body(new AppHeader(getTranslation("app.name"), presenter.viewer(), presenter.everyone(),
                this::switchViewer, this::render));

        var headline = Typography.displayMedium(headlineText());
        headline.addClassName("push-3xl");
        body(headline);

        var open = presenter.openPolls();
        if (!open.isEmpty()) {
            var rows = new Div();
            rows.addClassNames("stack-m", "push-xl");
            open.forEach(summary -> rows.add(rowFor(summary)));
            body(rows);
        }

        var settled = presenter.settledPolls();
        if (!settled.isEmpty()) {
            body(settledSection(settled));
        }

        var start = Actions.commit(getTranslation("polls.new"),
                ignored -> getUI().ifPresent(ui -> ui.navigate(NewPollView.class)));
        start.setIcon(new Icon(VaadinIcon.PLUS));
        footer(start);
    }

    private String headlineText() {
        var waiting = presenter.awaitingViewer();
        if (waiting == 0) {
            return getTranslation("polls.headline.none");
        }
        return waiting == 1
                ? getTranslation("polls.headline.one")
                : getTranslation("polls.headline.many", waiting);
    }

    private PollRow rowFor(PollSummary summary) {
        return new PollRow(summary,
                noteFor(summary),
                getTranslation("undecided"),
                stateChipFor(summary),
                () -> open(summary));
    }

    private String noteFor(PollSummary summary) {
        if (summary.hasHeadlineDay()) {
            return getTranslation("polls.leading",
                    Counts.progress(this, summary.voteCount(), summary.inviteCount()),
                    DateText.weekdayShort(this, summary.closesAt().toLocalDate()));
        }
        return getTranslation("polls.onTheTable",
                Counts.days(this, summary.candidateDayCount()),
                summary.askedBy().firstName());
    }

    private Component stateChipFor(PollSummary summary) {
        return summary.answeredByViewer()
                ? new Chip(getTranslation("polls.voted"), Chip.Tone.ACCENT)
                : new Chip(getTranslation("polls.vote"), Chip.Tone.SOLID);
    }

    /**
     * Where a row goes depends on what you are to the poll: the organizer watches
     * the counts, everybody else answers, or reads back the answer they gave.
     */
    private void open(PollSummary summary) {
        var parameters = new RouteParameters("slug", summary.slug());
        getUI().ifPresent(ui -> {
            if (summary.askedBy().equals(presenter.viewer())) {
                ui.navigate(ResultsView.class, parameters);
            } else if (summary.answeredByViewer()) {
                ui.navigate(ReceiptView.class, parameters);
            } else {
                ui.navigate(BallotView.class, parameters);
            }
        });
    }

    private Div settledSection(List<PollSummary> settled) {
        var section = new Div();
        section.addClassNames("stack-m", "push-3xl");
        section.add(Typography.sectionLabel(getTranslation("polls.settled")));
        var rows = new Div();
        rows.addClassName("stack");
        settled.forEach(summary -> rows.add(settledRow(summary)));
        section.add(rows);
        return section;
    }

    private Div settledRow(PollSummary summary) {
        var numeral = new Span(DateText.dayNumber(summary.headlineDay()));
        numeral.addClassName("settled-numeral");
        var title = new Span(summary.title());
        title.addClassName("settled-title");
        var date = new Span(DateText.dayAndMonth(this, summary.headlineDay()));
        date.addClassName("settled-date");
        var row = new Div(numeral, title, date);
        row.addClassName("settled-row");
        return row;
    }

    private void switchViewer(Person person) {
        presenter.switchViewer(person);
        render();
    }

    @Override
    public String getPageTitle() {
        return getTranslation("polls.title");
    }
}
