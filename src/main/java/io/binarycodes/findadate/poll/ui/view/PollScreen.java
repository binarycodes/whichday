package io.binarycodes.findadate.poll.ui.view;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.BeforeEnterObserver;
import com.vaadin.flow.router.HasDynamicTitle;
import com.vaadin.flow.router.RouteParameters;

import io.binarycodes.findadate.base.ui.Screen;
import io.binarycodes.findadate.poll.domain.Poll;
import io.binarycodes.findadate.poll.ui.presenter.PollPresenter;

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

    protected void goBack() {
        getUI().ifPresent(ui -> ui.navigate(PollsView.class));
    }
}
