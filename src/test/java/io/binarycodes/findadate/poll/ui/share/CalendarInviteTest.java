package io.binarycodes.findadate.poll.ui.share;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import io.binarycodes.findadate.people.domain.Person;
import io.binarycodes.findadate.poll.domain.Poll;

@DisplayName("The calendar file")
class CalendarInviteTest {

    private static final LocalDate MONDAY = LocalDate.of(2026, 9, 7);
    private static final LocalDate FRIDAY = LocalDate.of(2026, 9, 11);

    @Test
    @DisplayName("ends an all-day event on the following date, because iCalendar's end is exclusive")
    void allDayEventsAreExclusiveAtTheEnd() {
        var ics = CalendarInvite.calendar(poll("Q3 team offsite"), List.of(MONDAY), false);

        assertThat(ics).contains("DTSTART;VALUE=DATE:20260907")
                .contains("DTEND;VALUE=DATE:20260908")
                .contains("STATUS:CONFIRMED");
    }

    @Test
    @DisplayName("marks days that are only on the table as tentative, one event each")
    void candidateDaysAreTentative() {
        var ics = CalendarInvite.calendar(poll("Q3 team offsite"), List.of(MONDAY, FRIDAY), true);

        assertThat(ics).contains("STATUS:TENTATIVE");
        assertThat(ics.split("BEGIN:VEVENT", -1)).hasSize(3);
        assertThat(ics).startsWith("BEGIN:VCALENDAR").endsWith("END:VCALENDAR\r\n");
    }

    @Test
    @DisplayName("gives every event its own identifier, so two do not collapse into one")
    void eventsAreDistinct() {
        var ics = CalendarInvite.calendar(poll("Q3 team offsite"), List.of(MONDAY, FRIDAY), true);

        assertThat(ics).contains("UID:q3-team-offsite-0@findadate")
                .contains("UID:q3-team-offsite-1@findadate");
    }

    @Test
    @DisplayName("escapes the characters iCalendar reads as structure")
    void escapesTheTitle() {
        var ics = CalendarInvite.calendar(poll("Lunch, drinks; maybe \\ both"), List.of(MONDAY), false);

        assertThat(ics).contains("SUMMARY:Lunch\\, drinks\\; maybe \\\\ both");
    }

    @Test
    @DisplayName("folds every line the way the format requires")
    void usesCarriageReturns() {
        var ics = CalendarInvite.calendar(poll("Q3 team offsite"), List.of(MONDAY), false);

        assertThat(ics.lines()).allSatisfy(line -> assertThat(line).doesNotContain("\r"));
        assertThat(ics).doesNotContain("\n\n");
        assertThat(ics.split("\r\n")).allSatisfy(line -> assertThat(line).isNotBlank());
    }

    private Poll poll(String title) {
        var organizer = new Person("ada", "Ada Lindqvist", 0);
        return new Poll("q3-team-offsite", title, organizer, List.of(organizer),
                List.of(MONDAY, FRIDAY), null, null, null, List.of(), List.of());
    }
}
