package io.binarycodes.whichday.poll.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import io.binarycodes.whichday.people.domain.Person;
import io.binarycodes.whichday.people.service.AccountDirectory;
import io.binarycodes.whichday.poll.domain.AccountMatch;

@DisplayName("Searching for somebody to invite")
class InviteeSearchTest {

    private AccountDirectory directory;
    private InviteeSearch search;
    private Person organizer;

    @BeforeEach
    void setUp() {
        var clock = Clock.fixed(Instant.parse("2026-08-20T09:00:00Z"), ZoneOffset.UTC);
        directory = new AccountDirectory();
        search = new InviteeSearch(directory, new PollService(clock, directory));
        organizer = directory.defaultViewer();
    }

    @Test
    @DisplayName("says how often a match has decided a date with you")
    void reportsSharedHistory() {
        var matches = search.matching("sar", organizer, List.of());

        assertThat(matches).extracting(match -> match.person().email())
                .containsExactly("sara.naslund@acme.com", "t.sarkar@acme.com");
        assertThat(matches.getFirst().hasHistory()).isTrue();
        assertThat(matches.getLast().hasHistory()).isFalse();
    }

    @Test
    @DisplayName("puts the people you have decided with first")
    void ranksHistoryFirst() {
        var matches = search.matching("sar", organizer, List.of());

        assertThat(matches).isSortedAccordingTo(
                (left, right) -> Integer.compare(right.sharedPolls(), left.sharedPolls()));
    }

    @Test
    @DisplayName("counts only polls both of you actually answered")
    void countsAnsweredPollsOnly() {
        var jonas = directory.byEmail("jonas.wirtanen@acme.com").orElseThrow();

        var matches = search.matching("jonas", organizer, List.of());

        // Jonas has not answered the open offsite, but he did answer both settled
        // polls — so the history is the two they closed together, not three.
        assertThat(matches).extracting(AccountMatch::person).containsExactly(jonas);
        assertThat(matches.getFirst().sharedPolls()).isEqualTo(2);
    }

    @Test
    @DisplayName("finds nobody the organizer has never shared anything with counted as history")
    void aStrangerHasNoHistory() {
        var matches = search.matching("aronsson", organizer, List.of());

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
}
