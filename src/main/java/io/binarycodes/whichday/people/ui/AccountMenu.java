package io.binarycodes.whichday.people.ui;

import com.vaadin.flow.component.contextmenu.ContextMenu;

import io.binarycodes.whichday.people.domain.Person;

/**
 * The account, top right, where the design puts it. Tapping it offers the one thing
 * an account can do here: sign out.
 *
 * <p>A context menu on a bare avatar rather than a {@code MenuBar}: a menu bar brings
 * a button and an overflow arrow the design does not draw, and suppressing both means
 * styling into its shadow root.
 *
 * <p>The labels are handed in. Signing out of a provider and dropping a name you typed
 * are not the same act and do not read the same, and the component has no business
 * knowing which of the two it is offering.
 */
public class AccountMenu extends PersonAvatar {

    public AccountMenu(Person viewer, AccountLabels labels, Runnable onSignOut) {
        super(viewer);
        addClassName("account-avatar");
        getElement().setAttribute("title", viewer.displayName());

        var menu = new ContextMenu(this);
        menu.setOpenOnClick(true);
        menu.addItem(labels.signedInAs()).setEnabled(false);
        menu.addItem(labels.signOut(), ignored -> onSignOut.run());
    }
}
