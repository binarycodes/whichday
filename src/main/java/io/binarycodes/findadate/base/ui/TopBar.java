package io.binarycodes.findadate.base.ui;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.Icon;
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
        var back = new Button(new Icon(VaadinIcon.ANGLE_LEFT), ignored -> onBack.run());
        back.setAriaLabel(label);
        back.addClassNames("action-link");
        addComponentAsFirst(back);
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

    /** The title carries the row on its own and reads better hard against the edge. */
    public TopBar leadingTitle() {
        title.addClassName("top-bar-title-leading");
        return this;
    }
}
