package io.binarycodes.findadate.people.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import io.binarycodes.findadate.people.domain.Person;

@DisplayName("The team")
class TeamDirectoryTest {

    private final TeamDirectory directory = new TeamDirectory();

    @Test
    @DisplayName("is the seven people the design names")
    void members() {
        assertThat(directory.members()).hasSize(7);
        assertThat(directory.teamName()).isEqualTo("Design team");
        assertThat(directory.defaultViewer().firstName()).isEqualTo("Ada");
    }

    @Test
    @DisplayName("is looked up by id, and says so when there is no such person")
    void lookup() {
        assertThat(directory.byId("sara")).map(Person::name).contains("Sara Näslund");
        assertThat(directory.byId("nobody")).isEmpty();
    }

    @Test
    @DisplayName("takes initials from the first two words of a name")
    void initials() {
        assertThat(new Person("x", "Ada Lindqvist", 0).initials()).isEqualTo("AL");
        assertThat(new Person("x", "  Ada  Beatrice Lindqvist ", 0).initials()).isEqualTo("AB");
        assertThat(new Person("x", "Prince", 0).initials()).isEqualTo("P");
        assertThat(new Person("x", "Prince", 0).firstName()).isEqualTo("Prince");
    }
}
