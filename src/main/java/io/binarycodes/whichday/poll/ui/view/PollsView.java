package io.binarycodes.whichday.poll.ui.view;

import jakarta.annotation.security.PermitAll;

import java.util.List;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.BeforeEnterObserver;
import com.vaadin.flow.router.HasDynamicTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.router.RouteParameters;

import io.binarycodes.whichday.base.ui.Actions;
import io.binarycodes.whichday.base.ui.AppHeader;
import io.binarycodes.whichday.base.ui.Chip;
import io.binarycodes.whichday.base.ui.Counts;
import io.binarycodes.whichday.base.ui.DateText;
import io.binarycodes.whichday.base.ui.Screen;
import io.binarycodes.whichday.base.ui.Typography;
import io.binarycodes.whichday.poll.domain.PollSummary;
import io.binarycodes.whichday.poll.ui.component.DraftRow;
import io.binarycodes.whichday.poll.ui.component.PollRow;
import io.binarycodes.whichday.poll.ui.presenter.PollPresenter;

/**
 * Coming back: the polls you are part of, each carrying its own date numeral, and
 * the ones already settled underneath.
 */
@PermitAll
@Route("")
public class PollsView extends Screen implements BeforeEnterObserver, HasDynamicTitle {

    private final PollPresenter presenter;

    public PollsView(PollPresenter presenter) {
        this.presenter = presenter;
    }

    /**
     * Built on navigation rather than in the constructor: Vaadin reuses a view instance
     * when the route it is asked for is the one already showing, so a constructor-only
     * build leaves whatever it drew the first time.
     */
    @Override
    public void beforeEnter(BeforeEnterEvent event) {
        render();
    }

    private void render() {
        clearBody();
        clearFooter();

        body(new AppHeader(getTranslation("app.name"), presenter.viewer(),
                presenter::signOut, this::render));

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

        var drafts = presenter.draftPolls();
        if (!drafts.isEmpty()) {
            body(draftSection(drafts));
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

    /**
     * No qualifier on the numeral. Whether the day shown is decided is already carried
     * by where the row is: an open poll shows the day ahead on votes, a settled one
     * appears in the list below instead — so a word saying which would only repeat the
     * layout.
     */
    private String noteFor(PollSummary summary) {
        if (summary.isClosed()) {
            return getTranslation("polls.closed",
                    Counts.progress(this, summary.voteCount(), summary.inviteCount()));
        }
        if (summary.hasHeadlineDay()) {
            return getTranslation("polls.open",
                    Counts.progress(this, summary.voteCount(), summary.inviteCount()),
                    DateText.weekdayShort(this, summary.closesOn()));
        }
        return getTranslation("polls.onTheTable",
                Counts.days(this, summary.candidateDayCount()),
                summary.askedBy().firstName());
    }

    private Component stateChipFor(PollSummary summary) {
        if (summary.isClosed()) {
            return new Chip(getTranslation("polls.closedChip"), Chip.Tone.OUTLINE);
        }
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

    /**
     * Between the live polls and the settled ones: a draft is further along than
     * nothing and further back than sent, and the list reads in that order.
     */
    private Div draftSection(List<PollSummary> drafts) {
        var section = new Div();
        section.addClassNames("stack-m", "push-3xl");
        section.add(Typography.sectionLabel(getTranslation("polls.drafts")));
        var rows = new Div();
        rows.addClassName("stack");
        drafts.forEach(draft -> rows.add(draftRow(draft)));
        section.add(rows);
        return section;
    }

    private DraftRow draftRow(PollSummary draft) {
        return new DraftRow(draft, draftNoteFor(draft),
                () -> getUI().ifPresent(ui ->
                        ui.navigate(CandidateDaysView.class, new RouteParameters("slug", draft.slug()))),
                () -> deleteDraft(draft));
    }

    private String draftNoteFor(PollSummary draft) {
        return draft.candidateDayCount() == 0
                ? getTranslation("polls.draft.noDays")
                : getTranslation("polls.draft.days", Counts.days(this, draft.candidateDayCount()));
    }

    private void deleteDraft(PollSummary draft) {
        presenter.deleteDraft(draft.slug());
        Notification.show(getTranslation("polls.draft.deleted", draft.title()));
        render();
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

    @Override
    public String getPageTitle() {
        return getTranslation("polls.title");
    }
}
