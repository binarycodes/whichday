package io.binarycodes.whichday.base.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Reading a retention window")
class RetentionWindowTest {

    private static final String VARIABLE = "WHICHDAY_RETENTION_DAYS";
    private static final LocalDate TODAY = LocalDate.of(2026, 8, 20);

    @Test
    @DisplayName("turns a number of days into the day an anchor has to fall before")
    void cutoff() {
        assertThat(RetentionWindow.of(VARIABLE, "5").cutoff(TODAY)).contains(TODAY.minusDays(5));
        assertThat(RetentionWindow.of(VARIABLE, " 90 ").cutoff(TODAY)).contains(TODAY.minusDays(90));
    }

    /** Zero is a real answer: everything that has ended, on the day it ended. */
    @Test
    @DisplayName("takes zero as a window rather than as an absence")
    void zeroIsAWindow() {
        assertThat(RetentionWindow.of(VARIABLE, "0").cutoff(TODAY)).contains(TODAY);
    }

    @Test
    @DisplayName("has no cutoff at all when it is off, so the rule goes unrun")
    void never() {
        assertThat(RetentionWindow.of(VARIABLE, "never").cutoff(TODAY)).isEmpty();
        assertThat(RetentionWindow.of(VARIABLE, "Never").cutoff(TODAY)).isEmpty();
        assertThat(RetentionWindow.NEVER.cutoff(TODAY)).isEmpty();
    }

    /**
     * Quoting both, because the property the application reads is not the variable the
     * operator typed — and a window nobody can parse has no safe reading to fall back on.
     */
    @Test
    @DisplayName("refuses what it cannot read, naming the variable and the value")
    void unreadable() {
        assertThatThrownBy(() -> RetentionWindow.of(VARIABLE, "five"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining(VARIABLE)
                .hasMessageContaining("\"five\"")
                .hasMessageContaining("never");
        assertThatThrownBy(() -> RetentionWindow.of(VARIABLE, ""))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> RetentionWindow.of(VARIABLE, null))
                .isInstanceOf(IllegalStateException.class);
    }

    /** A negative window would delete a poll before the day it is measured from. */
    @Test
    @DisplayName("refuses a negative number of days")
    void negative() {
        assertThatThrownBy(() -> RetentionWindow.of(VARIABLE, "-1"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("\"-1\"");
    }
}
