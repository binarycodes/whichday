package io.binarycodes.whichday.people.ui.presenter;

import io.binarycodes.whichday.people.domain.Person;

/**
 * Who is signed in. An interface with one implementation, because the seam is what
 * lets a test say who the browser is — every route requires an authenticated user and
 * the real one comes from the identity provider.
 */
public interface ViewerSession {

    /**
     * The signed-in person. Throws rather than defaulting when nobody is: a fallback
     * viewer would put an unauthenticated path back into a store scoped by who you
     * are, which is the whole reason every route requires a login.
     */
    Person viewer();

    void signOut();
}
