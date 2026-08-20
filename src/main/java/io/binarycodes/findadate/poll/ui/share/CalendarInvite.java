package io.binarycodes.findadate.poll.ui.share;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

import com.vaadin.flow.server.streams.DownloadHandler;
import com.vaadin.flow.server.streams.DownloadResponse;

import io.binarycodes.findadate.poll.domain.Poll;

/**
 * The days as an iCalendar file. Whole days only, so every event is a DATE-valued
 * all-day entry — which is also why DTEND is the day after: iCalendar's end is
 * exclusive, and an inclusive one shows a one-day event as zero-length.
 */
public final class CalendarInvite {

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final String LINE_END = "\r\n";

    private CalendarInvite() {
    }

    /** Every day still on the table, so a voter can see the options against their week. */
    public static DownloadHandler forCandidateDays(Poll poll) {
        return download(poll.slug() + "-options.ics", calendar(poll, poll.candidateDays(), true));
    }

    /** The one day the team landed on. */
    public static DownloadHandler forLockedDay(Poll poll) {
        return download(poll.slug() + ".ics", calendar(poll, List.of(poll.lockedDay()), false));
    }

    private static DownloadHandler download(String fileName, String content) {
        var bytes = content.getBytes(StandardCharsets.UTF_8);
        return DownloadHandler.fromInputStream(ignored -> new DownloadResponse(
                new ByteArrayInputStream(bytes), fileName, "text/calendar", bytes.length));
    }

    static String calendar(Poll poll, List<LocalDate> days, boolean tentative) {
        var body = new StringBuilder("BEGIN:VCALENDAR").append(LINE_END)
                .append("VERSION:2.0").append(LINE_END)
                .append("PRODID:-//findadate//EN").append(LINE_END);
        for (var index = 0; index < days.size(); index++) {
            appendEvent(body, poll, days.get(index), index, tentative);
        }
        return body.append("END:VCALENDAR").append(LINE_END).toString();
    }

    private static void appendEvent(StringBuilder body, Poll poll, LocalDate day, int index, boolean tentative) {
        body.append("BEGIN:VEVENT").append(LINE_END)
                .append("UID:").append(poll.slug()).append('-').append(index).append("@findadate").append(LINE_END)
                .append("DTSTART;VALUE=DATE:").append(DATE.format(day)).append(LINE_END)
                .append("DTEND;VALUE=DATE:").append(DATE.format(day.plusDays(1))).append(LINE_END)
                .append("SUMMARY:").append(escape(poll.title())).append(LINE_END)
                .append("STATUS:").append(tentative ? "TENTATIVE" : "CONFIRMED").append(LINE_END)
                .append("END:VEVENT").append(LINE_END);
    }

    /** iCalendar treats comma, semicolon and backslash as structure. */
    private static String escape(String text) {
        return text.replace("\\", "\\\\").replace(";", "\\;").replace(",", "\\,");
    }
}
