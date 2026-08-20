package io.binarycodes.whichday.base.ui;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.VaadinIcon;

/**
 * Back, a title, and sometimes something on the right. When only one side carries
 * a control a spacer takes the other, so the title stays centred rather than
 * drifting toward whichever side is empty.
 */
public class TopBar extends Div {

    private final Span title = new Span();

    public TopBar(String titleText) {
        addClassName("top-bar");
        title.addClassName("top-bar-title");
        title.setText(titleText);
        add(title);
    }

    public TopBar withBack(String label, Runnable onBack) {
        addComponentAsFirst(Actions.icon(VaadinIcon.ANGLE_LEFT, label, ignored -> onBack.run()));
        return this;
    }

    /** A control before the title that is not a step back — the way home. */
    public TopBar withLeading(Component leading) {
        addComponentAsFirst(leading);
        return this;
    }

    public TopBar withTrailing(Component trailing) {
        add(trailing);
        return this;
    }

    /** Nothing on the right, so the title keeps the middle. */
    public TopBar withTrailingSpace() {
        var spacer = new Span();
        spacer.addClassName("top-bar-spacer");
        add(spacer);
        return this;
    }

    /**
     * The way out, in the slot the design keeps for it — screen 2 draws a menu glyph
     * there. It sits opposite the back chevron so that stepping back through a wizard
     * and leaving it altogether are never the same tap.
     */
    public TopBar withHome(String label, Runnable onHome) {
        var home = Actions.icon(VaadinIcon.HOME, label, ignored -> onHome.run());
        home.addClassName(Actions.HOME_CLASS);
        add(home);
        return this;
    }

    /** The title carries the row on its own and reads better hard against the edge. */
    public TopBar leadingTitle() {
        title.addClassName("top-bar-title-leading");
        return this;
    }
}
