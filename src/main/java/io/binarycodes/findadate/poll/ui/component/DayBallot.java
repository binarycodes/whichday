package io.binarycodes.findadate.poll.ui.component;

import java.time.LocalDate;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.customfield.CustomField;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.NativeButton;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.icon.VaadinIcon;

import io.binarycodes.findadate.base.ui.DateText;
import io.binarycodes.findadate.people.ui.AvatarStack;
import io.binarycodes.findadate.poll.domain.DayTally;

/**
 * Every day on the table, as a row you tap. Each row carries a numeral, the date,
 * who has said yes so far and a check — four pieces of content on one toggle, so a
 * native button with {@code aria-pressed} rather than a Vaadin button.
 */
public class DayBallot extends CustomField<Set<LocalDate>> {

    /** Above this many voters the row shows a count instead of faces. */
    private static final int FACE_LIMIT = 3;

    private final Div rows = new Div();
    private final Set<LocalDate> selection = new LinkedHashSet<>();
    private final LocalDate today;

    private List<DayTally> tallies = List.of();
    private NoteText noteText = (tally, leading) -> "";

    /** How a row describes the votes already on a day; the view owns the wording. */
    public interface NoteText {
        String describe(DayTally tally, boolean leading);
    }

    public DayBallot(LocalDate today) {
        this.today = today;
        rows.addClassName("stack-s");
        add(rows);
    }

    public void setNoteText(NoteText noteText) {
        this.noteText = noteText;
        render();
    }

    public void setTallies(List<DayTally> tallies) {
        this.tallies = List.copyOf(tallies);
        render();
    }

    @Override
    protected Set<LocalDate> generateModelValue() {
        return Set.copyOf(selection);
    }

    @Override
    protected void setPresentationValue(Set<LocalDate> days) {
        selection.clear();
        if (days != null) {
            selection.addAll(days);
        }
        render();
    }

    private void render() {
        rows.removeAll();
        tallies.stream().map(this::rowFor).forEach(rows::add);
    }

    private NativeButton rowFor(DayTally tally) {
        var row = new NativeButton();
        row.addClassName("day-row");
        row.add(numeralOf(tally.day()), mainOf(tally), checkMark());
        row.setAriaLabel(DateText.full(this, tally.day()));

        if (tally.day().isBefore(today)) {
            row.setEnabled(false);
        } else {
            row.getElement().setAttribute("aria-pressed", String.valueOf(selection.contains(tally.day())));
            row.addClickListener(event -> toggle(tally.day()));
        }
        return row;
    }

    private Div numeralOf(LocalDate day) {
        var number = new Div(new Span(DateText.dayNumber(day)));
        number.addClassName("day-row-number");
        var weekday = new Div(new Span(DateText.weekdayAbbreviation(this, day)));
        weekday.addClassName("day-row-weekday");
        var block = new Div(number, weekday);
        block.addClassName("day-row-numeral");
        return block;
    }

    private Div mainOf(DayTally tally) {
        var date = new Div(new Span(DateText.full(this, tally.day())));
        date.addClassName("day-row-date");
        var block = new Div(date);
        block.addClassName("day-row-main");
        block.add(supportOf(tally));
        return block;
    }

    /**
     * Faces while the group is small enough to recognise, a count once it is not.
     * The day in front always gets words, because "most popular" is the point of it.
     */
    private Component supportOf(DayTally tally) {
        var voters = tally.voters();
        if (!voters.isEmpty() && !tally.isLeading() && voters.size() <= FACE_LIMIT) {
            var stack = new AvatarStack(FACE_LIMIT);
            stack.addClassName("push-s");
            return stack.show(voters);
        }
        var note = new Div(new Span(noteText.describe(tally, tally.isLeading())));
        note.addClassName("day-row-note");
        return note;
    }

    private Span checkMark() {
        var check = new Span(new Icon(VaadinIcon.CHECK));
        check.addClassName("day-row-check");
        return check;
    }

    private void toggle(LocalDate day) {
        if (!selection.remove(day)) {
            selection.add(day);
        }
        render();
        updateValue();
    }
}
