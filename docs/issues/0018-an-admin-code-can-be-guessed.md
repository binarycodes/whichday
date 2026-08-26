# An admin code can be guessed, and nothing counts the guesses

**Severity:** medium — six digits are the whole of "may change this poll" in anonymous
mode, and there is no limit on how many times a caller may try them.

## What happens

`PollService.create` mints a six-digit code for every anonymous poll, and
`requireOrganizer` accepts it as a second way of being the organizer. Nothing else
stands in the way: `PollPresenter.lock`, `chooseDays`, `closeOn`, `send`,
`acceptProposal`, `allowAlternatives`, `addInvitee` and `deleteDraft` all reach it, and
a caller may put a code on their session as often as they like — the who-are-you screen
takes a new one every time it is submitted.

The space is 10^6. A script that already holds a poll's link can walk it in an
afternoon and settle somebody else's poll on a day of its choosing, or rewrite the days
under answers already given.

## Why it is not worse than it sounds

The code is compared with the poll being changed, never looked up across the table
(`carriesAdminCode`). So the guesser needs the link first, which means the poll was
shared with them or with somebody who passed it on. Anonymous mode already grants a
link-holder every read and a vote; what the code adds is the organizer's writes.

Six digits are also a decision rather than an accident: the code has to survive being
read down a phone line or typed from a screenshot, and that is what makes it short.

## What would fix it

Counting the attempts, not lengthening the code. A per-session and per-poll failure
counter with a delay after a handful of wrong answers takes the attack from an
afternoon to a geological age and costs the honest organizer nothing — they type their
code once.

`docs/issues/0015-response-hardening-stops-at-the-defaults.md` is the natural place for
it to live: both are about what an unauthenticated caller may do to this application in
volume, and neither has anything in front of it today.

## Where

- `src/main/java/io/binarycodes/whichday/poll/service/PollService.java` — `carriesAdminCode`, `requireOrganizer`
- `src/main/java/io/binarycodes/whichday/people/ui/view/IdentityView.java` — takes a code on every submit
- `src/main/java/io/binarycodes/whichday/people/ui/presenter/AnonymousViewerSession.java` — holds it for the session
