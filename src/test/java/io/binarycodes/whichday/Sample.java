package io.binarycodes.whichday;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import io.binarycodes.whichday.people.domain.Person;
import io.binarycodes.whichday.people.service.AccountDirectory;
import io.binarycodes.whichday.poll.domain.Caller;
import io.binarycodes.whichday.poll.service.PollService;

/**
 * The data the design was drawn with, as a fixture rather than as seeding.
 *
 * <p>It used to be production code. Once signing in became the only way in, an
 * account existed because somebody authenticated — so a hard-coded Ada Lindqvist had
 * nowhere to live in the application, and the polls she owned had nobody to own them.
 * The tests still want that shape, so it moved here.
 */
public final class Sample {

    public static final Person ADA = Person.signedIn("ada.lindqvist@acme.com", "Ada Lindqvist");
    public static final Person MIRO = Person.signedIn("m.kallio@acme.com", "Miro Kallio");
    public static final Person SARA = Person.signedIn("sara.naslund@acme.com", "Sara Näslund");
    public static final Person TOM = Person.signedIn("tom.beck@acme.com", "Tom Beck");
    public static final Person PRIYA = Person.signedIn("priya.rao@acme.com", "Priya Rao");
    public static final Person JONAS = Person.signedIn("jonas.wirtanen@acme.com", "Jonas Wirtanen");
    public static final Person LENA = Person.signedIn("lena.fors@acme.com", "Lena Fors");
    public static final Person TANVI = Person.signedIn("t.sarkar@acme.com", "Tanvi Sarkar");
    public static final Person SIXTEN = Person.signedIn("s.aronsson@acme.com", "Sixten Aronsson");

    /** The seven the design draws. Tanvi and Sixten sit outside it, so searching can narrow. */
    public static final List<Person> TEAM = List.of(ADA, MIRO, SARA, TOM, PRIYA, JONAS, LENA);
    public static final List<Person> EVERYBODY =
            List.of(ADA, MIRO, SARA, TOM, PRIYA, JONAS, LENA, TANVI, SIXTEN);

    private Sample() {
    }

    /**
     * Everybody has signed in at least once, which is how the directory knows them —
     * and, since a poll stores addresses and reads names from there, the only reason
     * anybody in a poll has a name at all.
     */
    public static void signedInBefore(AccountDirectory directory) {
        EVERYBODY.forEach(directory::remember);
    }

    /**
     * The Q3 offsite: five candidate days, six answers, standings of 6 / 4 / 3 / 2 / 1,
     * and Jonas holding out so the waiting list has a name.
     */
    public static UUID offsite(PollService service, Clock clock) {
        var monday = mondayAfterNext(LocalDate.now(clock));
        var days = List.of(monday, monday.plusDays(1), monday.plusDays(4),
                monday.plusDays(8), monday.plusDays(9));

        var id = service.create("Q3 team offsite", ADA, TEAM);
        service.replaceCandidateDays(id, Caller.of(ADA), days);
        service.send(id, Caller.of(ADA));
        service.castVote(id, ADA, Set.of(days.get(2), days.get(3)));
        service.castVote(id, MIRO, Set.of(days.get(0), days.get(2)));
        service.castVote(id, SARA, Set.of(days.get(0), days.get(2), days.get(3)));
        service.castVote(id, TOM, Set.of(days.get(1), days.get(2), days.get(3), days.get(4)));
        service.castVote(id, PRIYA, Set.of(days.get(2), days.get(3)));
        service.castVote(id, LENA, Set.of(days.get(0), days.get(1), days.get(2)));
        return id;
    }

    /** A poll nobody has answered yet, organized by somebody other than Ada. */
    public static UUID unanswered(PollService service, Clock clock) {
        var monday = mondayAfterNext(LocalDate.now(clock)).plusWeeks(3);
        var id = service.create("Design review week", MIRO, TEAM);
        service.replaceCandidateDays(id, Caller.of(MIRO),
                List.of(monday, monday.plusDays(1), monday.plusDays(2), monday.plusDays(4)));
        service.send(id, Caller.of(MIRO));
        return id;
    }

    /** A poll already decided, so the settled list has something in it. */
    public static UUID settled(PollService service, Clock clock) {
        var day = mondayAfterNext(LocalDate.now(clock)).plusWeeks(1);
        var id = service.create("Sprint 14 retro", ADA, TEAM);
        service.replaceCandidateDays(id, Caller.of(ADA), List.of(day));
        service.send(id, Caller.of(ADA));
        TEAM.forEach(person -> service.castVote(id, person, Set.of(day)));
        service.lock(id, Caller.of(ADA), day);
        return id;
    }

    /** Next week rather than this one, so every candidate day is still ahead. */
    public static LocalDate mondayAfterNext(LocalDate today) {
        return today.with(TemporalAdjusters.next(DayOfWeek.MONDAY)).plusWeeks(1);
    }
}
