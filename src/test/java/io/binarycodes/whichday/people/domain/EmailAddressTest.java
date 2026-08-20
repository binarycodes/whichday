package io.binarycodes.whichday.people.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Reading an address")
class EmailAddressTest {

    @Test
    @DisplayName("tells a finished address from one still being typed")
    void wellFormed() {
        assertThat(EmailAddress.isWellFormed("sara.naslund@acme.com")).isTrue();
        assertThat(EmailAddress.isWellFormed("  sara@acme.co  ")).isTrue();
        assertThat(EmailAddress.isWellFormed("sar")).isFalse();
        assertThat(EmailAddress.isWellFormed("jonas@acme")).isFalse();
        assertThat(EmailAddress.isWellFormed("@acme.com")).isFalse();
        assertThat(EmailAddress.isWellFormed("two @acme.com")).isFalse();
        assertThat(EmailAddress.isWellFormed(null)).isFalse();
    }

    @Test
    @DisplayName("splits a pasted list on commas, semicolons and newlines")
    void splitsAList() {
        assertThat(EmailAddress.split("tom@acme.com, priya@acme.com\njonas@acme"))
                .containsExactly("tom@acme.com", "priya@acme.com", "jonas@acme");
        assertThat(EmailAddress.looksPasted("tom@acme.com, priya@acme.com")).isTrue();
        assertThat(EmailAddress.looksPasted("tom@acme.com")).isFalse();
    }

    @Test
    @DisplayName("takes an address apart for searching, and leaves the domain out of it")
    void searchableParts() {
        assertThat(EmailAddress.searchableParts("sara.naslund@acme.com"))
                .containsExactly("sara", "naslund");
        assertThat(EmailAddress.searchableParts("t.sarkar@acme.com")).containsExactly("t", "sarkar");
        assertThat(EmailAddress.searchableParts("first+tag@acme.com")).containsExactly("first", "tag");
    }

    @Test
    @DisplayName("normalises case and surrounding space, because an address is one thing")
    void normalises() {
        assertThat(EmailAddress.normalise("  Sara.Naslund@ACME.com ")).isEqualTo("sara.naslund@acme.com");
    }
}
