package io.binarycodes.findadate.people.ui;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import com.vaadin.flow.component.checkbox.Checkbox;
import com.vaadin.flow.component.customfield.CustomField;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.NativeButton;
import com.vaadin.flow.component.html.Span;

import io.binarycodes.findadate.base.ui.Actions;
import io.binarycodes.findadate.base.ui.Counts;
import io.binarycodes.findadate.people.domain.Person;

/**
 * Who a poll goes to. The design draws this as a field showing three faces and a
 * summary, so that is what stays on the screen; tapping it opens the list the
 * organizer picks from.
 *
 * <p>The organizer is always in and cannot be taken out: they are deciding, and a
 * poll that excluded the person calling the meeting would count their availability
 * nowhere.
 */
public class TeamField extends CustomField<Set<Person>> {

    /** Enough faces to say "a team", without turning the field into a list. */
    private static final int VISIBLE_FACES = 3;

    private final List<Person> everyone;
    private final Person organizer;
    private final String teamName;
    private final Set<Person> chosen = new LinkedHashSet<>();
    private final AvatarStack faces = new AvatarStack(VISIBLE_FACES).hideOverflow();
    private final Span summary = new Span();

    public TeamField(List<Person> everyone, Person organizer, String teamName) {
        this.everyone = List.copyOf(everyone);
        this.organizer = organizer;
        this.teamName = teamName;
        this.chosen.addAll(everyone);

        summary.addClassNames("meta", "meta-faint");

        var box = new NativeButton();
        box.addClassName("team-field");
        box.getElement().setAttribute("aria-haspopup", "dialog");
        box.setAriaLabel(getTranslation("create.deciders"));
        box.add(faces, summary);
        box.addClickListener(ignored -> openPicker());

        add(box);
        render();
    }

    @Override
    protected Set<Person> generateModelValue() {
        return Set.copyOf(chosen);
    }

    @Override
    protected void setPresentationValue(Set<Person> people) {
        chosen.clear();
        chosen.addAll(people == null || people.isEmpty() ? everyone : people);
        chosen.add(organizer);
        render();
    }

    /** In directory order, so the invite list reads the same however it was picked. */
    public List<Person> chosenInOrder() {
        return everyone.stream().filter(chosen::contains).toList();
    }

    private void render() {
        faces.show(chosenInOrder());
        summary.setText(chosen.size() == everyone.size()
                ? getTranslation("create.team", teamName, Counts.people(this, chosen.size()))
                : getTranslation("create.team.subset", teamName, chosen.size(), everyone.size()));
    }

    private void openPicker() {
        var dialog = new Dialog();
        dialog.setHeaderTitle(getTranslation("create.deciders"));
        dialog.addClassName("team-picker");

        var rows = new Div();
        rows.addClassName("stack");
        everyone.forEach(person -> rows.add(rowFor(person)));
        dialog.add(rows);

        dialog.getFooter().add(Actions.primary(getTranslation("create.deciders.done"), ignored -> {
            dialog.close();
            render();
            updateValue();
        }));
        dialog.open();
    }

    private Div rowFor(Person person) {
        var name = new Span(person.name());
        name.addClassName("person-name");

        var included = new Checkbox(chosen.contains(person));
        if (person.equals(organizer)) {
            included.setEnabled(false);
            included.setAriaLabel(getTranslation("create.deciders.you", person.name()));
        } else {
            included.setAriaLabel(person.name());
            included.addValueChangeListener(event -> toggle(person, event.getValue()));
        }

        var row = new Div(new PersonAvatar(person), name, included);
        row.addClassName("person-row");
        return row;
    }

    private void toggle(Person person, boolean included) {
        if (included) {
            chosen.add(person);
        } else {
            chosen.remove(person);
        }
    }
}
