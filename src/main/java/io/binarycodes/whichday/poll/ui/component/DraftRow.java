package io.binarycodes.whichday.poll.ui.component;

import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Span;

import io.binarycodes.whichday.base.ui.Actions;
import io.binarycodes.whichday.poll.domain.PollSummary;

/**
 * A poll that was named and never sent, in the quiet list shape the settled polls
 * use — it is not something to answer, so it does not look like something to answer.
 *
 * <p>Deleting is confirmed on the row rather than in an overlay: the question is
 * about this one line, and it is short enough to ask there.
 */
public class DraftRow extends Div {

    private final PollSummary summary;
    private final String note;
    private final Runnable onEdit;
    private final Runnable onDelete;

    public DraftRow(PollSummary summary, String note, Runnable onEdit, Runnable onDelete) {
        this.summary = summary;
        this.note = note;
        this.onEdit = onEdit;
        this.onDelete = onDelete;
        addClassName("draft-row");
        showActions();
    }

    private void showActions() {
        removeAll();
        add(text(), Actions.link(getTranslation("polls.draft.edit"), ignored -> onEdit.run()),
                Actions.link(getTranslation("polls.draft.delete"), ignored -> confirmDelete()));
    }

    /** The row asks, in place, and offers the way back out first. */
    private void confirmDelete() {
        removeAll();
        var question = new Span(getTranslation("polls.draft.confirm"));
        question.addClassNames("draft-title", "draft-confirm");
        add(question, Actions.link(getTranslation("polls.draft.keep"), ignored -> showActions()),
                Actions.link(getTranslation("polls.draft.delete"), ignored -> onDelete.run()));
    }

    private Div text() {
        var title = new Span(summary.title());
        title.addClassName("draft-title");
        var detail = new Span(note);
        detail.addClassName("draft-note");
        var block = new Div(title, detail);
        block.addClassName("draft-text");
        return block;
    }
}
