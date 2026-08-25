package io.binarycodes.whichday.base.ui;

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

    /**
     * Marks whatever takes the reader back to their polls. Every screen carries one,
     * and a test asserts as much — so it is a name rather than a shape.
     */
    public static final String HOME_CLASS = "nav-home";

    private Actions() {
    }

    /** The step forward: accent paint, full width. */
    public static Button primary(String label, ComponentEventListener<ClickEvent<Button>> onClick) {
        return listening(primary(label), onClick);
    }

    /**
     * The same button with no listener of ours, for a click the browser handles
     * itself — sharing a link needs the gesture, and a round trip has already spent
     * it ({@link io.binarycodes.whichday.poll.ui.share.VotingLink#shareFrom}).
     */
    public static Button primary(String label) {
        return styled(new Button(label), "action", "action-primary");
    }

    /**
     * The step that ends something — send it out, lock it in. Solid ink rather than
     * accent, so that "this closes the voting" never looks like "next".
     */
    public static Button commit(String label, ComponentEventListener<ClickEvent<Button>> onClick) {
        return styled(new Button(label, onClick), "action", "action-commit");
    }

    public static Button outline(String label, ComponentEventListener<ClickEvent<Button>> onClick) {
        return listening(outline(label), onClick);
    }

    /** The same button with no listener of ours, for the reason {@link #primary(String)} gives. */
    public static Button outline(String label) {
        return styled(new Button(label), "action", "action-outline");
    }

    /** A small bordered button sitting inside a hint row — nudge, accept a proposal. */
    public static Button inline(String label, ComponentEventListener<ClickEvent<Button>> onClick) {
        return styled(new Button(label, onClick), "action", "action-outline", "action-inline");
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

    private static Button listening(Button button, ComponentEventListener<ClickEvent<Button>> onClick) {
        button.addClickListener(onClick);
        return button;
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
