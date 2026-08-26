package io.binarycodes.whichday.people.ui.presenter;

import java.util.Optional;

import io.binarycodes.whichday.people.domain.Person;

/**
 * Who is looking. One implementation per access mode, because the two answer the
 * question in ways that have nothing in common: login mode reads a token the provider
 * signed, anonymous mode remembers a name somebody typed.
 *
 * <p>The seam is also what lets a test say who the browser is.
 */
public interface ViewerSession {

    /**
     * The person looking. Throws rather than defaulting when there is nobody: a
     * fallback viewer would put an unattributed path into a store scoped by who you
     * are, and both modes have something standing in front of every screen to make
     * sure it cannot happen — the provider in one, the who-are-you screen in the other.
     */
    Person viewer();

    void signOut();

    /**
     * Whether anybody has said who they are yet. Always true in login mode, where the
     * filter chain has already turned away everybody who had not.
     */
    boolean isIdentified();

    /**
     * The admin code this session typed, if it typed one. Empty in login mode, where
     * being the organizer is decided by the address on the poll and by nothing else.
     */
    Optional<String> adminCode();

    /**
     * Names the session, and takes the admin code offered alongside the name. Login
     * mode has no use for either: the token said both.
     */
    void identify(String name, String adminCode);
}
