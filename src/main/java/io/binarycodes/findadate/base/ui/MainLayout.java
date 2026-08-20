package io.binarycodes.findadate.base.ui;

import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.router.Layout;
import com.vaadin.flow.router.RouterLayout;

/**
 * The app shell every route renders into. The design draws 390x844 phone frames,
 * so this centres one capped column against Aura's page background rather than
 * letting a mobile-first screen stretch across a desktop.
 */
@Layout
public class MainLayout extends Div implements RouterLayout {

    public MainLayout() {
        addClassName("app-shell");
    }
}
