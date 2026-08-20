package io.binarycodes.whichday.base.ui;

import com.vaadin.flow.component.html.Span;

/** A pill: a chosen day, a poll's state, or the badge on the locked screen. */
public class Chip extends Span {

    public enum Tone {
        ACCENT("chip-accent"),
        SOLID("chip-solid"),
        LIVE("chip-live"),
        OUTLINE("chip-outline");

        private final String className;

        Tone(String className) {
            this.className = className;
        }

        String className() {
            return className;
        }
    }

    public Chip(String label, Tone tone) {
        super(label);
        addClassNames("chip", tone.className());
    }
}
