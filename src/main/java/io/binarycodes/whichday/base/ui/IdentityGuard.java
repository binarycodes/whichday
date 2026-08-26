package io.binarycodes.whichday.base.ui;

import java.util.Optional;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.server.ServiceInitEvent;
import com.vaadin.flow.server.VaadinServiceInitListener;
import com.vaadin.flow.server.VaadinSession;

import io.binarycodes.whichday.people.ui.presenter.ViewerSession;
import io.binarycodes.whichday.people.ui.view.IdentityView;

/**
 * Nobody reaches a screen without saying who they are — anonymous mode's answer to the
 * redirect login mode's filter chain does.
 *
 * <p>It stands in front of every route, the shared voting link included: following one
 * asks for a name first and lands on the ballot after. The location is kept so that the
 * link survives the detour, which is the whole reason this is a guard and not a step in
 * the create flow.
 */
@Component
@ConditionalOnProperty(name = "whichday.access.mode", havingValue = "anonymous")
public class IdentityGuard implements VaadinServiceInitListener {

    private static final String WANTED = IdentityGuard.class.getName() + ".wanted";

    /**
     * Asked for one navigation at a time, not held. The session it hands back is scoped
     * to the Vaadin session, and this listener is a singleton the service builds long
     * before any browser has one — injecting the bean itself fails the context outright.
     */
    private final ObjectProvider<ViewerSession> sessions;

    public IdentityGuard(ObjectProvider<ViewerSession> sessions) {
        this.sessions = sessions;
    }

    @Override
    public void serviceInit(ServiceInitEvent event) {
        event.getSource().addUIInitListener(initialised ->
                initialised.getUI().addBeforeEnterListener(this::askWhoIsAsking));
    }

    private void askWhoIsAsking(BeforeEnterEvent event) {
        if (sessions.getObject().isIdentified() || event.getNavigationTarget() == IdentityView.class) {
            return;
        }
        VaadinSession.getCurrent().setAttribute(WANTED, event.getLocation().getPathWithQueryParameters());
        event.forwardTo(IdentityView.class);
    }

    /**
     * Where the browser was going before it was asked, taken rather than read: a
     * remembered destination that outlived the trip to it would send the next
     * navigation somewhere nobody asked for.
     */
    public static Optional<String> take() {
        var session = VaadinSession.getCurrent();
        var wanted = (String) session.getAttribute(WANTED);
        session.setAttribute(WANTED, null);
        return Optional.ofNullable(wanted).filter(path -> !path.isBlank());
    }
}
