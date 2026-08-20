package io.binarycodes.findadate.people.service;

import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;

import io.binarycodes.findadate.people.domain.Person;

/**
 * Who is on the team. A stand-in for the directory a signed-in deployment would
 * read the team from, which is why it is a service rather than a constant: the
 * screens already ask a bean for its members.
 */
@Service
public class TeamDirectory {

    private static final String TEAM_NAME = "Design team";

    private final List<Person> members = List.of(
            new Person("ada", "Ada Lindqvist", 0),
            new Person("miro", "Miro Kallio", 0),
            new Person("sara", "Sara Näslund", 1),
            new Person("tom", "Tom Beck", 2),
            new Person("priya", "Priya Rao", 3),
            new Person("jonas", "Jonas Wirtanen", 2),
            new Person("lena", "Lena Fors", 1));

    public List<Person> members() {
        return members;
    }

    public String teamName() {
        return TEAM_NAME;
    }

    /**
     * Who the browser is, until there is a login to ask. Every screen resolves the
     * viewer through here rather than assuming the organizer.
     */
    public Person defaultViewer() {
        return members.getFirst();
    }

    public Optional<Person> byId(String id) {
        return members.stream().filter(member -> member.id().equals(id)).findFirst();
    }
}
