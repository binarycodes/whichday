package io.binarycodes.whichday.people.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import io.binarycodes.whichday.Sample;
import io.binarycodes.whichday.TestDatabase;
import io.binarycodes.whichday.WhichdayTest;
import io.binarycodes.whichday.people.domain.Person;

@WhichdayTest
@DisplayName("Finding somebody by address")
class AccountDirectoryTest {

    private static final Person SEARCHER = Sample.ADA;

    @Autowired
    private AccountDirectory directory;

    @Autowired
    private TestDatabase database;

    @BeforeEach
    void everybodyHasSignedInBefore() {
        database.empty();
        Sample.signedInBefore(directory);
    }

    @Test
    @DisplayName("knows nobody until somebody has signed in")
    void startsEmpty() {
        database.empty();

        assertThat(directory.matching("sar", SEARCHER, List.of())).isEmpty();
        assertThat(directory.byEmail("sara.naslund@acme.com")).isEmpty();
        assertThat(directory.forInvite("sara.naslund@acme.com").hasAccount()).isFalse();
    }

    @Test
    @DisplayName("remembers whoever signs in, and takes their latest name")
    void remembersOnSignIn() {
        directory.remember(Person.signedIn("New.Person@acme.com", "New Person"));
        assertThat(directory.byEmail("new.person@acme.com")).map(Person::name).contains("New Person");

        directory.remember(Person.signedIn("new.person@acme.com", "Renamed Person"));
        assertThat(directory.byEmail("new.person@acme.com")).map(Person::name).contains("Renamed Person");
    }

    @Test
    @DisplayName("answers nothing at all under the minimum query length")
    void refusesToListPeople() {
        assertThat(directory.matching("s", SEARCHER, List.of())).isEmpty();
        assertThat(directory.matching("sa", SEARCHER, List.of())).isEmpty();
        assertThat(directory.matching("", SEARCHER, List.of())).isEmpty();
        assertThat(directory.matching(null, SEARCHER, List.of())).isEmpty();
    }

    @Test
    @DisplayName("matches the start of any part of an address, not just the whole of it")
    void matchesOnParts() {
        var found = directory.matching("sar", SEARCHER, List.of());

        assertThat(found).extracting(Person::email)
                .containsExactlyInAnyOrder("sara.naslund@acme.com", "t.sarkar@acme.com");
    }

    @Test
    @DisplayName("matches a whole address as it is typed out")
    void matchesAPrefix() {
        assertThat(directory.matching("sara.nas", SEARCHER, List.of()))
                .extracting(Person::email).containsExactly("sara.naslund@acme.com");
    }

    @Test
    @DisplayName("refuses to hand back a company from its domain")
    void doesNotMatchTheDomain() {
        assertThat(directory.matching("acme", SEARCHER, List.of())).isEmpty();
        assertThat(directory.matching("acme.com", SEARCHER, List.of())).isEmpty();
    }

    @Test
    @DisplayName("never offers the searcher themselves, or anybody already added")
    void excludesTheSearcherAndTheAdded() {
        var sara = directory.byEmail("sara.naslund@acme.com").orElseThrow();

        assertThat(directory.matching("ada", SEARCHER, List.of())).isEmpty();
        assertThat(directory.matching("sar", SEARCHER, List.of(sara)))
                .extracting(Person::email).containsExactly("t.sarkar@acme.com");
    }

    @Test
    @DisplayName("caps the answer, because a long list is a directory too")
    void capsTheAnswer() {
        assertThat(directory.matching("a", SEARCHER, List.of())).isEmpty();
        assertThat(directory.matching("s.a", SEARCHER, List.of())).hasSizeLessThanOrEqualTo(5);
    }

    @Test
    @DisplayName("says when the query was reaching for the searcher's own account")
    void recognisesTheSearcher() {
        var tom = directory.byEmail("tom.beck@acme.com").orElseThrow();

        assertThat(directory.matching("tom", tom, List.of())).isEmpty();
        assertThat(directory.matchesSearcher("tom", tom)).isTrue();
        assertThat(directory.matchesSearcher("tom.beck@acme.com", tom)).isTrue();
        assertThat(directory.matchesSearcher("sar", tom)).isFalse();
        assertThat(directory.matchesSearcher("to", tom)).isFalse();
        assertThat(directory.matchesSearcher(null, tom)).isFalse();
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
    @DisplayName("recomputes the avatar tone from the address rather than storing it")
    void toneSurvivesAReadWithoutBeingAColumn() {
        var sara = Sample.TEAM.stream()
                .filter(person -> person.email().equals("sara.naslund@acme.com"))
                .findFirst()
                .orElseThrow();

        assertThat(directory.byEmail(sara.email())).contains(sara);
        assertThat(directory.forInvite(sara.email()).avatarTone()).isEqualTo(sara.avatarTone());
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
