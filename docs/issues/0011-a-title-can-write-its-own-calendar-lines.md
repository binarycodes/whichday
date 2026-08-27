# A poll title can write its own lines into the calendar file

**Severity:** low — bounded by who can already reach the people affected, and the fix
is one more replacement.

## What happens

`CalendarInvite.escape` handles the three characters iCalendar treats as structure
within a value — backslash, semicolon, comma — and not the one that ends a value
outright. A title carrying `\r\n` closes `SUMMARY:` and whatever follows it is read
as iCalendar rather than as text:

    SUMMARY:Team lunch
    URL:https://not-us.example/collect
    BEGIN:VEVENT
    ...

Both downloads are affected: the candidate days from the share screen and the settled
day from the locked screen. Whoever imports the file gets whatever the title said —
an extra event, a recurrence rule, a link, an attendee.

A single-line `TextField` will not produce a newline by typing or pasting, so this
needs a crafted value update rather than a browser. Nothing on the server side stops
one: the field's value is a string and it is stored as it arrives.

## What it costs

The organizer already chooses the title, already chooses who is invited, and already
has a mail draft addressed to them — so this buys an attacker reach they largely
have. What it does buy is content the reader has no reason to distrust: a calendar
entry does not look like something the person who sent it wrote by hand.

## What fixing it looks like

Escape the line ending as RFC 5545 asks, in `escape`, alongside the three that are
already there — `\r\n` and a bare `\n` both become a literal `\n`, and a stray `\r`
goes. `CalendarInviteTest` is where the case belongs.

Worth doing at the same time, for the same reason and in the same method: the format folds
lines longer than 75 octets. A title is capped at fifty characters now, at the field and in
the column, so a `SUMMARY:` line can no longer run long on a title alone — but folding is
the format's rule rather than a consequence of length, and the method is where it belongs.
