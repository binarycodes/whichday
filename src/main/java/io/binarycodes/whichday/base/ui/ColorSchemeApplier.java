package io.binarycodes.whichday.base.ui;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import com.vaadin.flow.server.ServiceInitEvent;
import com.vaadin.flow.server.VaadinServiceInitListener;

/**
 * Paints each new UI with the scheme the session settled on, so a reload comes back the
 * way the reader left it rather than reverting to their system setting.
 *
 * <p>Here rather than in {@link MainLayout}, and that is not a preference. A router
 * layout is built without a Vaadin session scope to resolve against, so naming a
 * session-scoped bean as a constructor parameter there stops the layout being created at
 * all — silently, taking the app shell with it. A view may inject one; a layout may not.
 *
 * <p>Asked for one UI at a time through an {@code ObjectProvider} for the reason
 * {@link IdentityGuard} gives: this listener is a singleton the service builds long
 * before any browser has a session, so holding the bean itself fails the context.
 */
@Component
public class ColorSchemeApplier implements VaadinServiceInitListener {

    private final ObjectProvider<ColorSchemeChoice> choices;

    public ColorSchemeApplier(ObjectProvider<ColorSchemeChoice> choices) {
        this.choices = choices;
    }

    @Override
    public void serviceInit(ServiceInitEvent event) {
        event.getSource().addUIInitListener(initialised ->
                choices.getObject().applyTo(initialised.getUI()));
    }
}
