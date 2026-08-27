package io.binarycodes.whichday.people.ui;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.Key;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.data.value.ValueChangeMode;

/**
 * The digits that say a poll is yours to change, as a box per digit.
 *
 * <p>One field would hold the same string. A box per digit is how a code that arrives read
 * down a phone line or cropped out of a screenshot actually gets entered: the eye keeps its
 * place, and a digit that went in wrong is a digit somebody can see.
 *
 * <p>Two behaviours are what keep that from being tedious. Typing a digit moves to the next
 * box, so the code goes in without a tab between digits. And a whole code arriving at once
 * — pasted — is spread across the boxes rather than truncated, because somebody who copied
 * six digits should not have to cut them up. A code as long as the field fills the field
 * from the start, wherever it was dropped.
 */
public class AdminCodeField extends Div {

    private final List<TextField> boxes;
    private final int length;

    /** Set while a code is being spread, so the writes that makes are not read as typing. */
    private boolean spreading;

    public AdminCodeField(int length) {
        this.length = length;
        this.boxes = new ArrayList<>(length);
        addClassName("code-boxes");
        for (var index = 0; index < length; index++) {
            boxes.add(box(index));
        }
        add(boxes.toArray(Component[]::new));
    }

    /** What has been typed so far, which is only a code once {@link #isComplete} is true. */
    public String value() {
        return boxes.stream().map(TextField::getValue).collect(Collectors.joining());
    }

    /** A code is every box or none: five digits is a typo rather than a shorter code. */
    public boolean isComplete() {
        return value().length() == length;
    }

    public boolean isEmpty() {
        return value().isEmpty();
    }

    public void clear() {
        spreading = true;
        try {
            boxes.forEach(TextField::clear);
        } finally {
            spreading = false;
        }
    }

    public void focusFirst() {
        boxes.getFirst().focus();
    }

    private TextField box(int index) {
        var box = new TextField();
        box.addClassName("code-box");
        box.setAllowedCharPattern("[0-9]");
        box.setValueChangeMode(ValueChangeMode.EAGER);
        // Selected on focus, so typing into a box that already holds a digit replaces it.
        // Without this the new digit reads as a second character and gets pushed into the
        // next box, which is not what somebody correcting one digit of six is asking for.
        box.setAutoselect(true);
        box.addValueChangeListener(event -> arrived(index, event.getValue()));
        box.addKeyDownListener(Key.BACKSPACE, event -> steppedBack(index));
        return box;
    }

    /**
     * What landed in one box. A single digit is somebody typing, and the next box is where
     * they are going next. More than one is a whole code arriving at once.
     */
    private void arrived(int index, String value) {
        if (spreading) {
            return;
        }
        if (value.length() > 1) {
            spread(index, value);
        } else if (value.length() == 1 && index + 1 < length) {
            boxes.get(index + 1).focus();
        }
    }

    private void spread(int from, String code) {
        spreading = true;
        try {
            var digits = code.replaceAll("\\D", "");
            if (digits.isEmpty()) {
                // Nothing to spread, and the box is holding whatever arrived. Emptying it
                // is what keeps a box to one character: the pattern turns typed rubbish
                // away at the field, and this turns away rubbish that got past it.
                boxes.get(from).clear();
                return;
            }
            var box = digits.length() >= length ? 0 : from;
            for (var position = 0; position < digits.length() && box < length; position++, box++) {
                boxes.get(box).setValue(String.valueOf(digits.charAt(position)));
            }
            boxes.get(Math.min(box, length - 1)).focus();
        } finally {
            spreading = false;
        }
    }

    /**
     * Backspace in a box that is already empty goes back a box and empties that one, which
     * is how the digit before the caret gets corrected without reaching for the mouse. A box
     * with a digit in it keeps the keystroke — that press is what clears the digit.
     */
    private void steppedBack(int index) {
        if (index == 0 || !boxes.get(index).isEmpty()) {
            return;
        }
        var previous = boxes.get(index - 1);
        previous.clear();
        previous.focus();
    }
}
