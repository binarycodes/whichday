package io.binarycodes.whichday.base.ui;

import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.page.ColorScheme;
import com.vaadin.flow.spring.annotation.VaadinSessionScope;

import org.springframework.stereotype.Component;

/**
 * Light, dark, or whatever the device says, for as long as the session lasts.
 *
 * <p>The session is as far as it goes, and that is the whole of why this is a bean
 * rather than a column. Anonymous mode has nothing that outlives the tab, and login
 * mode stores a person and nothing else about them ({@code docs/REQUIREMENTS.md} §10) —
 * so a reader who has not chosen this time is served by their own system setting, which
 * is a better answer than one this application picked for them.
 */
@Component
@VaadinSessionScope
public class ColorSchemeChoice {

    private ColorScheme.Value chosen = ColorScheme.Value.SYSTEM;

    public ColorScheme.Value chosen() {
        return chosen;
    }

    public void choose(ColorScheme.Value value) {
        chosen = value;
    }

    /**
     * Paints a page with the remembered choice, as a reload is a new page that has not
     * been told.
     *
     * <p>The two lines are what {@code Page.setColorScheme} runs, written out because
     * that method finishes by recording the value on the page's extended client details
     * — which do not exist yet when a UI is still initialising. The resulting
     * {@code NullPointerException} takes the rest of the initialisation with it, and the
     * router layout never attaches: the whole app shell disappears, silently. Verified,
     * not guessed.
     */
    public void applyTo(UI ui) {
        ui.getPage().executeJs("document.documentElement.setAttribute('theme', $0);"
                + "document.documentElement.style.colorScheme = $1;",
                chosen.getThemeValue(), chosen.getValue());
    }
}
