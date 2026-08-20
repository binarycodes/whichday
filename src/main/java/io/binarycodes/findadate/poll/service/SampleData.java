package io.binarycodes.findadate.poll.service;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.temporal.TemporalAdjusters;
import java.util.List;
import java.util.Set;

import io.binarycodes.findadate.people.domain.Person;
import io.binarycodes.findadate.people.service.AccountDirectory;

/**
 * The data the design was drawn with: the Q3 offsite, seven people, five
 * candidate days, and standings of 6 / 4 / 3 / 2 / 1.
 *
 * <p>Dates are anchored to the clock rather than written out, so the sample stays
 * in the future as the calendar moves. The design's own numerals — Mon 14, Fri 18 —
 * are a September 2026 reading of exactly this arithmetic.
 */
final class SampleData {

    private static final LocalTime CLOSING_TIME = LocalTime.of(18, 0);
    private static final int OFFSET_MON = 0;
    private static final int OFFSET_TUE = 1;
    private static final int OFFSET_FRI = 4;
    private static final int OFFSET_NEXT_TUE = 8;
    private static final int OFFSET_NEXT_WED = 9;

    private SampleData() {
    }

    static void seed(PollService service, AccountDirectory directory, Clock clock) {
        var organizer = directory.byEmail("ada.lindqvist@acme.com").orElseThrow();
        var miro = directory.byEmail("m.kallio@acme.com").orElseThrow();
        var sara = directory.byEmail("sara.naslund@acme.com").orElseThrow();
        var tom = directory.byEmail("tom.beck@acme.com").orElseThrow();
        var priya = directory.byEmail("priya.rao@acme.com").orElseThrow();
        var lena = directory.byEmail("lena.fors@acme.com").orElseThrow();
        // The seven the design draws, not every account there is: the directory also
        // holds people outside this team, so that searching has something to narrow.
        var everyone = List.of(organizer, miro, sara, tom, priya,
                directory.byEmail("jonas.wirtanen@acme.com").orElseThrow(),
                lena);

        var monday = firstMondayAfter(LocalDate.now(clock));
        var day14 = monday.plusDays(OFFSET_MON);
        var day15 = monday.plusDays(OFFSET_TUE);
        var day18 = monday.plusDays(OFFSET_FRI);
        var day22 = monday.plusDays(OFFSET_NEXT_TUE);
        var day23 = monday.plusDays(OFFSET_NEXT_WED);

        var offsite = service.create("Q3 team offsite", organizer, everyone);
        service.replaceCandidateDays(offsite, List.of(day14, day15, day18, day22, day23));
        service.closeAt(offsite, day18.minusDays(1).atTime(CLOSING_TIME));
        service.castVote(offsite, organizer, Set.of(day18, day22));
        service.castVote(offsite, miro, Set.of(day14, day18));
        service.castVote(offsite, sara, Set.of(day14, day18, day22));
        service.castVote(offsite, tom, Set.of(day15, day18, day22, day23));
        service.castVote(offsite, priya, Set.of(day18, day22));
        service.castVote(offsite, lena, Set.of(day14, day15, day18));

        // Jonas is deliberately left out: he is the holdout the results screen
        // offers to nudge, and the name on "Everyone but Jonas".

        var review = service.create("Design review week", miro, everyone);
        service.replaceCandidateDays(review,
                List.of(monday.plusWeeks(3), monday.plusWeeks(3).plusDays(OFFSET_TUE),
                        monday.plusWeeks(3).plusDays(2), monday.plusWeeks(3).plusDays(OFFSET_FRI)));
        service.send(review);

        var lunch = service.create("Lena's leaving lunch", tom, everyone);
        service.replaceCandidateDays(lunch,
                List.of(monday.plusWeeks(4).plusDays(2), monday.plusWeeks(4).plusDays(3),
                        monday.plusWeeks(4).plusDays(OFFSET_FRI)));
        service.send(lunch);

        seedSettled(service, "Sprint 14 retro", organizer, everyone, monday.minusWeeks(4).plusDays(OFFSET_FRI));
        seedSettled(service, "Summer party", tom, everyone, monday.minusWeeks(6).plusDays(OFFSET_FRI));
    }

    private static void seedSettled(PollService service,
                                    String title,
                                    Person organizer,
                                    List<Person> invited,
                                    LocalDate settledOn) {
        var slug = service.create(title, organizer, invited);
        service.replaceCandidateDays(slug, List.of(settledOn));
        invited.forEach(person -> service.castVote(slug, person, Set.of(settledOn)));
        service.lock(slug, settledOn);
    }

    /**
     * The Monday that starts the sample fortnight. Next week rather than this one,
     * so every candidate day is still ahead and the calendar has nothing greyed out
     * in the middle of the run.
     */
    private static LocalDate firstMondayAfter(LocalDate today) {
        return today.with(TemporalAdjusters.next(DayOfWeek.MONDAY)).plusWeeks(1);
    }
}
