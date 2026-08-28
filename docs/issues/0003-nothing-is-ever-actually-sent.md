# An invite is never actually sent

**Severity:** medium — invite-by-address is half a feature without a transport.

## What happens

There is no mail transport, so nothing the application would send is sent:

- **Invites.** "Send 7 invites" opens the poll for voting. It does not email anybody
  the link, so somebody invited by address who has never signed in has no way to
  learn the poll exists.
- **Bounces.** The design's C3 frame names a bounced invite as a state to show.
  Nothing can bounce.

What *does* work is sharing by hand, and it is honest about being by hand: "Share link"
hands the link to the system share sheet — the reader's own mail app among everything
else on the device — and copies it to the clipboard where there is no sheet. The sheet's
text names the address the recipient has to sign in with, which is the one thing the
organizer cannot improvise.

## Why it is like this

Sending mail needs a transport, a sender identity and a deliverability story, none of
which existed. The buttons were built because the screens are built from the design,
and the design has them.

## What was done in the meantime

The nudge, the reminder and the locked screen's "Tell the team" are gone, in both modes
— see REQUIREMENTS §11. Two of them lied outright and the third made a promise the
application could not keep on its own, so the honest thing was to stop offering them
rather than let them stand in for a transport that does not exist.

That leaves the invite, which is the one that matters: an invitee who cannot be told
about the poll cannot answer it. It cannot be dropped the same way, because inviting by
address is what login mode *is* — so this ticket stays open until there is something
behind it.

## What fixing it looks like

A transport, a sender identity and a deliverability story. Once the invite sends, the
reminder becomes possible on the same footing — it also needs a scheduler, which
retention has since brought (REQUIREMENTS §9) — and the bounce state has something real
to report.
