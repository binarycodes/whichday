package io.binarycodes.whichday.base.ui;

import com.vaadin.flow.component.Component;

import io.binarycodes.whichday.poll.ui.presenter.PollPresenter;
import io.binarycodes.whichday.poll.ui.view.NewPollView;
import io.binarycodes.whichday.poll.ui.view.PollsView;

/**
 * Where the way home goes, which is not the same screen in both modes.
 *
 * <p>Login mode has a list of the polls you are part of. Anonymous mode has no way to
 * know what you are part of — no account, no invitations, nothing that outlives the
 * session — so there is nothing to list, and starting a new poll is what home means
 * there instead.
 */
public final class Home {

    private Home() {
    }

    public static Class<? extends Component> viewFor(PollPresenter presenter) {
        return presenter.anonymous() ? NewPollView.class : PollsView.class;
    }

    /** What the way home is called, since it does not go to the same place. */
    public static String labelFor(Component owner, PollPresenter presenter) {
        return owner.getTranslation(presenter.anonymous() ? "nav.home.anonymous" : "nav.home");
    }
}
