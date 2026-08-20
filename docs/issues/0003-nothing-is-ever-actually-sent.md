# Nudges, reminders and invites are never actually sent

**Severity:** medium — the UI promises things the application does not do.

## What happens

There is no mail transport, so nothing the interface offers to send is sent:

- **Nudge.** The results screen offers to chase whoever has not answered, and
  reports "Nudged Jonas". Nothing leaves the application.
- **The reminder.** The empty-state screen says "A reminder goes out tomorrow
  morning." Nothing is scheduled and nothing runs.
- **Invites.** "Send 7 invites" opens the poll for voting. It does not email anybody
  the link, so somebody invited by address who has never signed in has no way to
  learn the poll exists.
- **Bounces.** The design's C3 frame names a bounced invite as a state to show.
  Nothing can bounce.

What *does* work is sharing by hand: "Copy" puts the real link on the clipboard, and
"Message" opens a `mailto:` the reader's own client sends.

## Why it is like this

Sending mail needs a transport, a sender identity and a deliverability story, none of
which existed. The buttons were built because the screens are built from the design,
and the design has them.

## What fixing it looks like

Least dishonest first: **the invite is the one that matters.** An invitee who cannot
be told about the poll cannot answer it, which makes invite-by-email half a feature.
Nudges and reminders are polish on top of the same transport.

Until there is one, the honest alternative is to stop offering what does not happen —
drop the nudge button and the reminder sentence rather than have them lie.
