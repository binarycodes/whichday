package io.binarycodes.whichday.base.ui;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.vaadin.flow.component.page.ColorScheme;

@DisplayName("Which way the page is painted")
class ColorSchemeChoiceTest {

    /**
     * A session that has not been asked follows the device. Anything else would be this
     * application overriding a setting the reader already made, somewhere else.
     */
    @Test
    @DisplayName("follows the device until somebody says otherwise")
    void systemUntilChosen() {
        assertThat(new ColorSchemeChoice().chosen()).isEqualTo(ColorScheme.Value.SYSTEM);
    }

    @Test
    @DisplayName("keeps the choice that was made")
    void remembersAChoice() {
        var choice = new ColorSchemeChoice();

        choice.choose(ColorScheme.Value.DARK);
        assertThat(choice.chosen()).isEqualTo(ColorScheme.Value.DARK);

        choice.choose(ColorScheme.Value.LIGHT);
        assertThat(choice.chosen()).isEqualTo(ColorScheme.Value.LIGHT);
    }

    /** Choosing the device's answer back is a choice, not a reset to nothing. */
    @Test
    @DisplayName("takes the device's answer back as a choice of its own")
    void systemIsChoosableAgain() {
        var choice = new ColorSchemeChoice();
        choice.choose(ColorScheme.Value.DARK);

        choice.choose(ColorScheme.Value.SYSTEM);

        assertThat(choice.chosen()).isEqualTo(ColorScheme.Value.SYSTEM);
    }
}
