package io.binarycodes.findadate.base.ui;

import com.vaadin.flow.component.AttachEvent;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.page.ColorScheme;
import com.vaadin.flow.router.Layout;
import com.vaadin.flow.router.RouterLayout;

/**
 * The app shell every route renders into. The design draws 390x844 phone frames, so
 * this centres one capped column against Aura's page background rather than letting
 * a mobile-first screen stretch across a desktop.
 */
@Layout
public class MainLayout extends Div implements RouterLayout {

    public MainLayout() {
        addClassName("app-shell");
    }

    /**
     * Light and dark are Aura's colour scheme, and the application makes no choice of
     * its own — there is nowhere to remember one yet, and a design drawn in light that
     * ignores a reader's dark preference is worse than one that follows it.
     */
    @Override
    protected void onAttach(AttachEvent event) {
        super.onAttach(event);
        event.getUI().getPage().setColorScheme(ColorScheme.Value.SYSTEM);
    }
}
