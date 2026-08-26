package io.binarycodes.whichday.people.ui.presenter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.ZoneOffset;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import io.binarycodes.whichday.AnonymousWhichdayTest;
import io.binarycodes.whichday.TestClock;
import io.binarycodes.whichday.TestDatabase;
import io.binarycodes.whichday.people.service.AccountDirectory;

/**
 * Identity when there is no provider.
 *
 * <p>The session is built by hand rather than injected: it is session-scoped, so there
 * is no Vaadin session to resolve one from here, and a test that wants two visitors
 * wants two of them. Its own clock, too, so that moving time on cannot disturb the
 * shared one.
 */
@AnonymousWhichdayTest
@DisplayName("A session that only ever typed a name")
class AnonymousViewerSessionTest {

    @Autowired
    private AccountDirectory directory;

    @Autowired
    private TestDatabase database;

    private TestClock clock;
    private AnonymousViewerSession session;

    @BeforeEach
    void setUp() {
        database.empty();
        clock = new TestClock(TestClock.START, ZoneOffset.UTC);
        session = new AnonymousViewerSession(clock, directory);
    }

    @Test
    @DisplayName("has nobody in it until somebody says who they are")
    void nobodyYet() {
        assertThat(session.isIdentified()).isFalse();
        assertThatThrownBy(session::viewer)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Nobody has said who they are");
    }

    @Test
    @DisplayName("mints an address nobody could have typed, stamped with the moment")
    void mintsAnAddress() {
        session.identify("Ada", "");

        assertThat(session.viewer().name()).isEqualTo("Ada");
        assertThat(session.viewer().displayName()).isEqualTo("Ada");
        assertThat(session.viewer().email())
                .endsWith("-20260820t090000@whichday.anonymous")
                .matches("[0-9a-f-]{36}-[0-9t]+@whichday\\.anonymous");
    }

    /**
     * The address is what every poll, ballot and invitee row this session writes will be
     * keyed on. A second one would make the same person a stranger to their own answers,
     * so correcting a name must not mint one — not even after the clock has moved.
     */
    @Test
    @DisplayName("keeps the address it minted when the name is corrected")
    void keepsTheAddress() {
        session.identify("Ada", "");
        var minted = session.viewer().email();

        clock.advanceDays(1);
        session.identify("Ada Lindqvist", "");

        assertThat(session.viewer().email()).isEqualTo(minted);
        assertThat(session.viewer().name()).isEqualTo("Ada Lindqvist");
    }

    /**
     * Two people who typed the same name at the same moment are still two people. The
     * timestamp is for reading, not for telling anybody apart — the UUID is.
     */
    @Test
    @DisplayName("gives two sessions two addresses, however close together they start")
    void twoSessionsAreTwoPeople() {
        session.identify("Ada", "");
        var other = new AnonymousViewerSession(clock, directory);
        other.identify("Ada", "");

        assertThat(other.viewer().email()).isNotEqualTo(session.viewer().email());
    }

    /**
     * A poll stores addresses and reads names from the account table, so a name that
     * was not written there is a name nobody else on the poll ever sees.
     */
    @Test
    @DisplayName("puts the name where every other screen reads names from")
    void theNameIsReadableByEverybodyElse() {
        session.identify("Ada", "");

        assertThat(directory.forInvite(session.viewer().email()).name()).isEqualTo("Ada");
        assertThat(database.rowsIn("account")).isEqualTo(1);

        session.identify("Ada Lindqvist", "");

        assertThat(directory.forInvite(session.viewer().email()).name()).isEqualTo("Ada Lindqvist");
        assertThat(database.rowsIn("account")).isEqualTo(1);
    }

    @Test
    @DisplayName("carries the admin code it was given, and nothing when it was given none")
    void carriesTheAdminCode() {
        session.identify("Ada", "  ");
        assertThat(session.adminCode()).isEmpty();

        session.identify("Ada", " 483920 ");
        assertThat(session.adminCode()).contains("483920");
    }
}
