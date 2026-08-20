package io.binarycodes.whichday.base.ui;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.html.Div;

/**
 * One screen of the design: a column that fills the viewport, with a footer the
 * primary action sits in. The footer is pinned to the bottom rather than following
 * the last paragraph, which is what the design's phone frames rely on.
 */
public class Screen extends Div {

    private final Div body = new Div();
    private final Div footer = new Div();

    protected Screen() {
        addClassName("screen");
        body.addClassName("screen-body");
        footer.addClassName("screen-footer");
        add(body, footer);
    }

    protected void body(Component... components) {
        body.add(components);
    }

    protected void footer(Component... components) {
        footer.add(components);
    }

    protected void clearBody() {
        body.removeAll();
    }

    protected void clearFooter() {
        footer.removeAll();
    }
}
