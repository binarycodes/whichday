package io.binarycodes.whichday.people.ui;

import com.vaadin.flow.component.Component;

import io.binarycodes.whichday.people.domain.Person;

/**
 * What the account menu says, which is not the same in both modes: one is a provider
 * you are signed in to, the other is a name you typed. The choice is made here once
 * rather than at every screen that draws the menu — the same reason {@code Counts}
 * exists.
 */
public record AccountLabels(String signedInAs, String signOut) {

    public static AccountLabels of(Component owner, Person viewer, boolean anonymous) {
        if (anonymous) {
            return new AccountLabels(owner.getTranslation("nav.youAre", viewer.displayName()),
                    owner.getTranslation("nav.startOver"));
        }
        return new AccountLabels(owner.getTranslation("nav.signedInAs", viewer.displayName()),
                owner.getTranslation("nav.signOut"));
    }
}
