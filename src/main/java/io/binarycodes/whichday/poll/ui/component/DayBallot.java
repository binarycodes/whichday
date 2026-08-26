package io.binarycodes.whichday.poll.ui.component;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.customfield.CustomField;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.NativeButton;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.icon.VaadinIcon;

import io.binarycodes.whichday.base.ui.DateText;
import io.binarycodes.whichday.people.domain.Person;
import io.binarycodes.whichday.people.ui.AvatarStack;
import io.binarycodes.whichday.people.ui.NameChips;
import io.binarycodes.whichday.poll.domain.DayTally;

/**
 * Every day on the table, as a row you tap. Each row carries a numeral, the date,
 * who has said yes so far and a check — four pieces of content on one toggle, so a
 * native button with {@code aria-pressed} rather than a Vaadin button.
 */
public class DayBallot extends CustomField<Set<LocalDate>> {

    /** Above this many voters the row shows a count instead of faces. */
    private static final int FACE_LIMIT = 3;

    /**
     * Names are wider than faces but they wrap, so a row holds more of them before it
     * has to give up and count. Past this the tail becomes "+37 more".
     */
    private static final int NAME_LIMIT = 6;

    private final Div rows = new Div();
    private final Map<LocalDate, NativeButton> rowsByDay = new HashMap<>();
    private final Set<LocalDate> selection = new LinkedHashSet<>();
    private final LocalDate today;

    private List<DayTally> tallies = List.of();
    private NoteText noteText = (tally, leading) -> "";
    private boolean namesRatherThanFaces;

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

    /**
     * Name the voters instead of showing their faces.
     *
     * <p>An avatar is initials, and initials only identify anybody when the names
     * behind them were settled in advance. Where a voter types their own name minutes
     * before answering, one letter is as likely to be a stranger's as a colleague's —
     * so the name goes on the row in full.
     *
     * <p>An opt-in the caller makes rather than something read from the mode here: this
     * component is told what to draw, and never learns why.
     */
    public DayBallot withVoterNames() {
        this.namesRatherThanFaces = true;
        render();
        return this;
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
        rowsByDay.clear();
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
            rowsByDay.put(tally.day(), row);
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
        if (namesRatherThanFaces) {
            return namedSupportOf(tally);
        }
        var voters = tally.voters();
        if (!voters.isEmpty() && !tally.isLeading() && voters.size() <= FACE_LIMIT) {
            return facesOf(voters);
        }
        return noteOf(tally);
    }

    /**
     * Names wrap, so a row can carry them whatever the count — there is no size at
     * which it has to fall back to a number the way the faces do. The day in front
     * keeps its words as well, because "most popular" is the reason to look at it and
     * a row of names does not say that.
     */
    private Component namedSupportOf(DayTally tally) {
        var voters = tally.voters();
        if (voters.isEmpty()) {
            return noteOf(tally);
        }
        return tally.isLeading() ? new Div(noteOf(tally), namesOf(voters)) : namesOf(voters);
    }

    private Component facesOf(List<Person> voters) {
        var stack = new AvatarStack(FACE_LIMIT);
        stack.addClassName("push-s");
        return stack.show(voters);
    }

    private Div noteOf(DayTally tally) {
        var note = new Div(new Span(noteText.describe(tally, tally.isLeading())));
        note.addClassName("day-row-note");
        return note;
    }

    private Div namesOf(List<Person> voters) {
        var row = NameChips.of(this, voters, NAME_LIMIT);
        row.addClassName("day-row-voters");
        return row;
    }

    private Span checkMark() {
        var check = new Span(new Icon(VaadinIcon.CHECK));
        check.addClassName("day-row-check");
        return check;
    }

    /**
     * Only aria-pressed changes: a row's note counts other people's votes, which your
     * own tap does not move. Rebuilding the list would throw away the row that was
     * just pressed and the caret with it.
     */
    private void toggle(LocalDate day) {
        if (!selection.remove(day)) {
            selection.add(day);
        }
        var row = rowsByDay.get(day);
        if (row != null) {
            row.getElement().setAttribute("aria-pressed", String.valueOf(selection.contains(day)));
        }
        updateValue();
    }
}
