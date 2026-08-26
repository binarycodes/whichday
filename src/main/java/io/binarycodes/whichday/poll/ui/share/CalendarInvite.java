package io.binarycodes.whichday.poll.ui.share;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

import com.vaadin.flow.server.streams.DownloadHandler;
import com.vaadin.flow.server.streams.DownloadResponse;

import io.binarycodes.whichday.poll.domain.Poll;

/**
 * The settled day as an iCalendar file. Whole days only, so the event is a DATE-valued
 * all-day entry — which is also why DTEND is the day after: iCalendar's end is
 * exclusive, and an inclusive one shows a one-day event as zero-length.
 *
 * <p>Only a decided day is offered. The share screen used to hand out the candidate
 * days as TENTATIVE events, which put every maybe in the reader's calendar for them to
 * delete once the poll settled on one of them.
 */
public final class CalendarInvite {

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final String LINE_END = "\r\n";

    private CalendarInvite() {
    }

    /** The one day the team landed on. */
    public static DownloadHandler forLockedDay(Poll poll) {
        return download(poll.id() + ".ics", calendar(poll, List.of(poll.lockedDay())));
    }

    private static DownloadHandler download(String fileName, String content) {
        var bytes = content.getBytes(StandardCharsets.UTF_8);
        return DownloadHandler.fromInputStream(ignored -> new DownloadResponse(
                new ByteArrayInputStream(bytes), fileName, "text/calendar", bytes.length));
    }

    static String calendar(Poll poll, List<LocalDate> days) {
        var body = new StringBuilder("BEGIN:VCALENDAR").append(LINE_END)
                .append("VERSION:2.0").append(LINE_END)
                .append("PRODID:-//whichday//EN").append(LINE_END);
        for (var index = 0; index < days.size(); index++) {
            appendEvent(body, poll, days.get(index), index);
        }
        return body.append("END:VCALENDAR").append(LINE_END).toString();
    }

    private static void appendEvent(StringBuilder body, Poll poll, LocalDate day, int index) {
        body.append("BEGIN:VEVENT").append(LINE_END)
                .append("UID:").append(poll.id()).append('-').append(index).append("@whichday").append(LINE_END)
                .append("DTSTART;VALUE=DATE:").append(DATE.format(day)).append(LINE_END)
                .append("DTEND;VALUE=DATE:").append(DATE.format(day.plusDays(1))).append(LINE_END)
                .append("SUMMARY:").append(escape(poll.title())).append(LINE_END)
                .append("STATUS:CONFIRMED").append(LINE_END)
                .append("END:VEVENT").append(LINE_END);
    }

    /** iCalendar treats comma, semicolon and backslash as structure. */
    private static String escape(String text) {
        return text.replace("\\", "\\\\").replace(";", "\\;").replace(",", "\\,");
    }
}
