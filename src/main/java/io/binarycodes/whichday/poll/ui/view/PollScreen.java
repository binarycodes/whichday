package io.binarycodes.whichday.poll.ui.view;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.BeforeEnterObserver;
import com.vaadin.flow.router.HasDynamicTitle;
import com.vaadin.flow.router.RouteParameters;

import io.binarycodes.whichday.base.ui.Actions;
import io.binarycodes.whichday.base.ui.Screen;
import io.binarycodes.whichday.poll.domain.Poll;
import io.binarycodes.whichday.poll.ui.presenter.PollPresenter;

/**
 * A screen about one poll. The slug only arrives with the navigation event, so the
 * content is built there rather than in the constructor — and a slug nobody
 * recognises is forwarded away before anything tries to read it.
 */
abstract class PollScreen extends Screen implements BeforeEnterObserver, HasDynamicTitle {

    protected final PollPresenter presenter;

    private String slug;

    protected PollScreen(PollPresenter presenter) {
        this.presenter = presenter;
    }

    @Override
    public void beforeEnter(BeforeEnterEvent event) {
        slug = event.getRouteParameters().get("slug").orElse("");
        var poll = presenter.poll(slug);
        if (poll.isEmpty()) {
            event.forwardTo(NotFoundView.class);
            return;
        }
        if (redirect(event, poll.get())) {
            return;
        }
        render();
    }

    /**
     * A chance to send the navigation somewhere else before anything is built —
     * forwarded, rather than navigated to from inside the event, so the browser sees
     * one navigation and the abandoned screen is never rendered.
     *
     * @return true when the event was forwarded and this screen should not build
     */
    protected boolean redirect(BeforeEnterEvent event, Poll poll) {
        return false;
    }

    protected void forwardToPoll(BeforeEnterEvent event, Class<? extends Component> view) {
        event.forwardTo(view, new RouteParameters("slug", slug));
    }

    protected String slug() {
        return slug;
    }

    /** Re-read the poll and rebuild, after an action that changed it. */
    protected void render() {
        clearBody();
        clearFooter();
        build(presenter.poll(slug).orElseThrow());
    }

    protected abstract void build(Poll poll);

    protected void goTo(Class<? extends Component> view) {
        getUI().ifPresent(ui -> ui.navigate(view, new RouteParameters("slug", slug)));
    }

    protected void goHome() {
        getUI().ifPresent(ui -> ui.navigate(PollsView.class));
    }

    /**
     * The way out of a screen that has no step to go back to. Carries the
     * {@code nav-home} marker every home affordance does, whatever shape it takes —
     * an icon here, the wordmark on the screens that open with one, a footer button
     * on the not-found screen.
     */
    protected Button homeButton() {
        var home = Actions.icon(VaadinIcon.HOME, getTranslation("nav.home"), ignored -> goHome());
        home.addClassName(Actions.HOME_CLASS);
        return home;
    }
}
