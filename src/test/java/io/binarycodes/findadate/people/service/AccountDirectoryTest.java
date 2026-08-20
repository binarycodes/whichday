package io.binarycodes.findadate.people.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import io.binarycodes.findadate.people.domain.Person;

@DisplayName("Finding somebody by address")
class AccountDirectoryTest {

    private final AccountDirectory directory = new AccountDirectory();
    private final Person searcher = directory.defaultViewer();

    @Test
    @DisplayName("answers nothing at all under the minimum query length")
    void refusesToListPeople() {
        assertThat(directory.matching("s", searcher, List.of())).isEmpty();
        assertThat(directory.matching("sa", searcher, List.of())).isEmpty();
        assertThat(directory.matching("", searcher, List.of())).isEmpty();
        assertThat(directory.matching(null, searcher, List.of())).isEmpty();
    }

    @Test
    @DisplayName("matches the start of any part of an address, not just the whole of it")
    void matchesOnParts() {
        var found = directory.matching("sar", searcher, List.of());

        assertThat(found).extracting(Person::email)
                .containsExactlyInAnyOrder("sara.naslund@acme.com", "t.sarkar@acme.com");
    }

    @Test
    @DisplayName("matches a whole address as it is typed out")
    void matchesAPrefix() {
        assertThat(directory.matching("sara.nas", searcher, List.of()))
                .extracting(Person::email).containsExactly("sara.naslund@acme.com");
    }

    @Test
    @DisplayName("refuses to hand back a company from its domain")
    void doesNotMatchTheDomain() {
        assertThat(directory.matching("acme", searcher, List.of())).isEmpty();
        assertThat(directory.matching("acme.com", searcher, List.of())).isEmpty();
    }

    @Test
    @DisplayName("never offers the searcher themselves, or anybody already added")
    void excludesTheSearcherAndTheAdded() {
        var sara = directory.byEmail("sara.naslund@acme.com").orElseThrow();

        assertThat(directory.matching("ada", searcher, List.of())).isEmpty();
        assertThat(directory.matching("sar", searcher, List.of(sara)))
                .extracting(Person::email).containsExactly("t.sarkar@acme.com");
    }

    @Test
    @DisplayName("caps the answer, because a long list is a directory too")
    void capsTheAnswer() {
        assertThat(directory.matching("a", searcher, List.of())).isEmpty();
        assertThat(directory.matching("s.a", searcher, List.of())).hasSizeLessThanOrEqualTo(5);
    }

    @Test
    @DisplayName("turns an address with no account behind it into somebody invitable")
    void invitesAnOutsider() {
        var outsider = directory.forInvite("Lena.Ohlsson@studiofern.se");

        assertThat(outsider.email()).isEqualTo("lena.ohlsson@studiofern.se");
        assertThat(outsider.hasAccount()).isFalse();
        assertThat(outsider.displayName()).isEqualTo("lena.ohlsson@studiofern.se");
        assertThat(outsider.initials()).isEqualTo("LO");
        assertThat(directory.forInvite("lena.ohlsson@studiofern.se").avatarTone())
                .isEqualTo(outsider.avatarTone());
    }

    @Test
    @DisplayName("returns the account when one answers to the address")
    void invitesAKnownAccount() {
        assertThat(directory.forInvite("SARA.NASLUND@acme.com").name()).isEqualTo("Sara Näslund");
    }

    @Test
    @DisplayName("takes initials from a name, or from the address when there is none")
    void initials() {
        assertThat(new Person("x@y.z", "Ada Lindqvist", 0).initials()).isEqualTo("AL");
        assertThat(new Person("x@y.z", "Prince", 0).initials()).isEqualTo("P");
        assertThat(Person.outsider("tom.beck@acme.com").initials()).isEqualTo("TB");
        assertThat(Person.outsider("tom@acme.com").initials()).isEqualTo("T");
        assertThat(Person.outsider("tom@acme.com").firstName()).isEqualTo("tom");
    }
}
