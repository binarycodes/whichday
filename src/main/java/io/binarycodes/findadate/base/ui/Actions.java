package io.binarycodes.findadate.base.ui;

import com.vaadin.flow.component.ClickEvent;
import com.vaadin.flow.component.ComponentEventListener;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.icon.VaadinIcon;

/**
 * The design's buttons, in one place. A chromeless button is Aura's {@code TERTIARY}
 * variant rather than a class of ours that overrides a background: the variant is
 * what the theme's own base styles yield to, and setting the custom property alone
 * leaves the border and the hover behind.
 */
public final class Actions {

    private Actions() {
    }

    /** The step forward: accent paint, full width. */
    public static Button primary(String label, ComponentEventListener<ClickEvent<Button>> onClick) {
        return styled(new Button(label, onClick), "action", "action-primary");
    }

    /**
     * The step that ends something — send it out, lock it in. Solid ink rather than
     * accent, so that "this closes the voting" never looks like "next".
     */
    public static Button commit(String label, ComponentEventListener<ClickEvent<Button>> onClick) {
        return styled(new Button(label, onClick), "action", "action-commit");
    }

    public static Button outline(String label, ComponentEventListener<ClickEvent<Button>> onClick) {
        return styled(new Button(label, onClick), "action", "action-outline");
    }

    /** A small bordered button sitting inside a hint row. */
    public static Button inline(String label, ComponentEventListener<ClickEvent<Button>> onClick) {
        return styled(tertiary(new Button(label, onClick)), "action", "action-outline", "action-inline");
    }

    /** Text that behaves like a link — "Clear", "Edit", "None of these work". */
    public static Button link(String label, ComponentEventListener<ClickEvent<Button>> onClick) {
        return styled(tertiary(new Button(label, onClick)), "action-link");
    }

    /** An icon on its own: back, and the two month arrows. */
    public static Button icon(VaadinIcon glyph, String ariaLabel,
                              ComponentEventListener<ClickEvent<Button>> onClick) {
        var button = tertiary(new Button(new Icon(glyph), onClick));
        button.setAriaLabel(ariaLabel);
        return styled(button, "action-icon");
    }

    private static Button tertiary(Button button) {
        button.addThemeVariants(ButtonVariant.TERTIARY);
        return button;
    }

    private static Button styled(Button button, String... classNames) {
        button.addClassNames(classNames);
        return button;
    }
}
