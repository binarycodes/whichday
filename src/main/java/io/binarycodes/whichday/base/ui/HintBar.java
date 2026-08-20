package io.binarycodes.whichday.base.ui;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.icon.VaadinIcon;

/**
 * An icon, a sentence, and sometimes one action: the closing-time note, the
 * reminder, the nudge prompt. Two weights — a tinted panel, or an outlined card
 * where the design gives the row a border instead.
 */
public class HintBar extends Div {

    private final Span text = new Span();

    public HintBar(VaadinIcon icon, String message) {
        addClassName("hint");
        var glyph = new Icon(icon);
        glyph.addClassName("hint-icon");
        text.addClassName("hint-text");
        text.setText(message);
        add(glyph, text);
    }

    public HintBar outlined() {
        addClassName("hint-outlined");
        return this;
    }

    public HintBar withAction(Component action) {
        add(action);
        return this;
    }
}
