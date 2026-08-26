package io.binarycodes.whichday.poll.ui.share;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import io.binarycodes.whichday.people.domain.Person;
import io.binarycodes.whichday.poll.domain.Poll;
import io.binarycodes.whichday.poll.domain.PollState;

@DisplayName("The calendar file")
class CalendarInviteTest {

    private static final UUID OFFSITE = UUID.fromString("3f2a1c8e-5b9d-4e7a-8c6f-1d2e3a4b5c6d");

    private static final LocalDate MONDAY = LocalDate.of(2026, 9, 7);
    private static final LocalDate FRIDAY = LocalDate.of(2026, 9, 11);

    @Test
    @DisplayName("ends an all-day event on the following date, because iCalendar's end is exclusive")
    void allDayEventsAreExclusiveAtTheEnd() {
        var ics = CalendarInvite.calendar(poll("Q3 team offsite"), List.of(MONDAY));

        assertThat(ics).contains("DTSTART;VALUE=DATE:20260907")
                .contains("DTEND;VALUE=DATE:20260908")
                .contains("STATUS:CONFIRMED");
    }

    /**
     * Only a settled day is ever offered, so there is no TENTATIVE case left to cover.
     * The multi-day shape stays exercised because the writer still loops.
     */
    @Test
    @DisplayName("wraps one event per day in a single calendar")
    void eachDayIsItsOwnEvent() {
        var ics = CalendarInvite.calendar(poll("Q3 team offsite"), List.of(MONDAY, FRIDAY));

        assertThat(ics).doesNotContain("STATUS:TENTATIVE");
        assertThat(ics.split("BEGIN:VEVENT", -1)).hasSize(3);
        assertThat(ics).startsWith("BEGIN:VCALENDAR").endsWith("END:VCALENDAR\r\n");
    }

    @Test
    @DisplayName("gives every event its own identifier, so two do not collapse into one")
    void eventsAreDistinct() {
        var ics = CalendarInvite.calendar(poll("Q3 team offsite"), List.of(MONDAY, FRIDAY));

        assertThat(ics).contains("UID:" + OFFSITE + "-0@whichday")
                .contains("UID:" + OFFSITE + "-1@whichday");
    }

    @Test
    @DisplayName("escapes the characters iCalendar reads as structure")
    void escapesTheTitle() {
        var ics = CalendarInvite.calendar(poll("Lunch, drinks; maybe \\ both"), List.of(MONDAY));

        assertThat(ics).contains("SUMMARY:Lunch\\, drinks\\; maybe \\\\ both");
    }

    @Test
    @DisplayName("folds every line the way the format requires")
    void usesCarriageReturns() {
        var ics = CalendarInvite.calendar(poll("Q3 team offsite"), List.of(MONDAY));

        assertThat(ics.lines()).allSatisfy(line -> assertThat(line).doesNotContain("\r"));
        assertThat(ics).doesNotContain("\n\n");
        assertThat(ics.split("\r\n")).allSatisfy(line -> assertThat(line).isNotBlank());
    }

    private Poll poll(String title) {
        var organizer = new Person("ada", "Ada Lindqvist", 0);
        return new Poll(OFFSITE, title, organizer, List.of(organizer),
                List.of(MONDAY, FRIDAY), null, null, null, true, PollState.OPEN,
                List.of(), List.of());
    }
}
