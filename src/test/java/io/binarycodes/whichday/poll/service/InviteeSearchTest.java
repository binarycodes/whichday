package io.binarycodes.whichday.poll.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import io.binarycodes.whichday.Sample;
import io.binarycodes.whichday.TestClock;
import io.binarycodes.whichday.TestDatabase;
import io.binarycodes.whichday.WhichdayTest;
import io.binarycodes.whichday.people.service.AccountDirectory;
import io.binarycodes.whichday.poll.domain.AccountMatch;

@WhichdayTest
@DisplayName("Searching for somebody to invite")
class InviteeSearchTest {

    @Autowired
    private TestClock clock;

    @Autowired
    private TestDatabase database;

    @Autowired
    private AccountDirectory directory;

    @Autowired
    private PollService polls;

    @Autowired
    private InviteeSearch search;

    @BeforeEach
    void setUp() {
        database.empty();
        clock.reset();
        Sample.signedInBefore(directory);
        Sample.offsite(polls, clock);
    }

    @Test
    @DisplayName("says how often a match has decided a date with you")
    void reportsSharedHistory() {
        var matches = search.matching("sar", Sample.ADA, List.of());

        assertThat(matches).extracting(match -> match.person().email())
                .containsExactly("sara.naslund@acme.com", "t.sarkar@acme.com");
        assertThat(matches.getFirst().hasHistory()).isTrue();
        assertThat(matches.getLast().hasHistory()).isFalse();
    }

    @Test
    @DisplayName("puts the people you have decided with first")
    void ranksHistoryFirst() {
        var matches = search.matching("sar", Sample.ADA, List.of());

        assertThat(matches).isSortedAccordingTo(
                (left, right) -> Integer.compare(right.sharedPolls(), left.sharedPolls()));
    }

    @Test
    @DisplayName("counts only polls both of you actually answered")
    void countsAnsweredPollsOnly() {
        // Jonas has not answered the offsite, so he and Ada share nothing yet.
        var matches = search.matching("jonas", Sample.ADA, List.of());

        assertThat(matches).extracting(AccountMatch::person).containsExactly(Sample.JONAS);
        assertThat(matches.getFirst().sharedPolls()).isZero();
    }

    @Test
    @DisplayName("finds somebody with no shared history at all")
    void aStrangerHasNoHistory() {
        var matches = search.matching("aronsson", Sample.ADA, List.of());

        assertThat(matches).extracting(match -> match.person().email())
                .containsExactly("s.aronsson@acme.com");
        assertThat(matches.getFirst().sharedPolls()).isZero();
    }

    @Test
    @DisplayName("resolves an unknown address to an invitation rather than an error")
    void resolvesAnOutsider() {
        assertThat(search.hasAccount("lena.ohlsson@studiofern.se")).isFalse();
        assertThat(search.inviteFor("lena.ohlsson@studiofern.se").hasAccount()).isFalse();
        assertThat(search.hasAccount("sara.naslund@acme.com")).isTrue();
        assertThat(search.inviteFor("sara.naslund@acme.com").name()).isEqualTo("Sara Näslund");
    }

    @Test
    @DisplayName("finds nobody at all before anybody has signed in")
    void anEmptyDirectoryFindsNobody() {
        database.empty();

        assertThat(search.matching("sar", Sample.ADA, List.of())).isEmpty();
        assertThat(search.hasAccount("sara.naslund@acme.com")).isFalse();
    }
}
