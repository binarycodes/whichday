# Requirements and decisions

What Whichday has to do, and every decision taken getting there with the reason it
was taken. The design left plenty open and the implementation had to settle it; this
is the record of what was settled and why, so that the next person changing something
knows which lines are load-bearing.

[`issues/`](issues/) is the companion: what is still wrong, one file each.

**The product.** Put a few days on the table, let a group tap every one that works,
and settle on the day with the most votes. Whole days only, multi-select voting, one
poll shared by everybody who was asked. Mobile first.

---

## 1. Signing in is the only way in

OIDC, and nothing else. There is no login view — `oauth2LoginPage` points straight at
the registration, so an unauthenticated request redirects to the provider rather than
to a form of ours. The application collects no credentials and never sees one.

Every route carries `@PermitAll`, which in Vaadin means *authenticated*. There is no
anonymous path to any screen, including a ballot.

**It refuses to start without credentials.** Spring will not do this for us: an
unresolved `${...}` placeholder binds as the literal string, so the application would
start happily, fetch the provider's discovery document, and redirect to a real
authorization endpoint carrying a client id of `${WHICHDAY_OIDC_CLIENT_ID}`. The first
person to try signing in meets the provider's error page. Signing in is the only way
in, so a missing client is a startup failure rather than a surprise later.

**The registration is called `oidc`, not after the provider behind it.** Spring builds
`/oauth2/authorization/oidc` and the `/login/oauth2/code/oidc` callback from that id,
so a vendor name here would end up in the application's own URLs and in every
deployment's configuration. Which provider it is belongs to the issuer URI and to
nothing else.

**The name comes off the token, not out of a form.** `AuthenticatedViewerSession.viewer()`
reads the OIDC `name` claim and calls `AccountDirectory.remember` on the way past, which
is the only write to the `account` table anywhere. An account exists because somebody
authenticated — never because a list said so. `remember` writes nothing when the name
has not changed, which matters because `viewer()` runs on every screen render.

An account whose provider withholds an email falls back to the OIDC subject, which is
stable. Such a person can create polls; they cannot be invited to anybody else's,
because a subject is not something an organizer can type into an invite field.

---

## 2. Who may see a poll, and who may change it

Three permissions, and they nest.

| | |
| --- | --- |
| `poll(id, viewer)` | the organizer or an invitee — and a draft is the organizer's alone |
| `openPolls`, `settledPolls` | scoped in the query, both arms indexed |
| `draftPolls` | organizer only |
| `castVote`, `decline` | anybody on the invitee list |
| `send`, `closeOn`, `replaceCandidateDays`, `acceptProposal`, `allowAlternatives`, `lock`, `addInvitee`, `deleteDraft` | the organizer alone |

Enforced in `PollService`, never on the screens. The screens do hide what is not
yours, and that is a courtesy; a hidden button is not a check.

**The match is on the address.** You sign in with the address you were invited at and
nothing else works: not another address of yours, not an alias, not a colleague's
account at the same company. `bob+team@example.com` and `bob@example.com` are two
different people as far as a poll is concerned. That is the price of matching on
addresses rather than on accounts, and matching on accounts is not available — an
invitee may never have signed in, so there is no id to compare.

`plannedClosing` and `latestClosingDay` are a deliberate exception: they are reads,
they say only what the ballot already shows, and the one screen that calls them turns
a non-organizer away at the door.

### What this replaced

Signing in was always required, but nothing asked *which* signed-in person was asking.
Two holes came of that, and the second was much the worse:

- Anybody with the link could read the title, the whole invitee list, who voted for
  what, and every counter-proposal — and add a vote of their own.
- **`openPolls` and `settledPolls` returned every poll in the database.** No link
  needed: the home screen listed strangers' polls to anybody who signed in.

Three strings in the interface promised the opposite and had never been true of the
implementation — "No sign-up for voters. One link, one tap.", "Nothing found. We'll
email a voting link instead.", "They'll get a voting link by email and can answer
without signing up." All three now say that everyone signs in with the address they
were invited at, and the invite mail says it too, because the refusal deliberately
explains nothing and the mail is the only place a reader can be told which address to
use.

`isOrganizer` also used to gate nothing at all. It had exactly one caller — `BallotView`
deciding where to navigate after a submit — so the results screen offered "Lock in
Thursday" and "Add it" to every invitee, and the service took both without asking.
`/poll/:id/days` and `/poll/:id/share` had no redirect either, so any invitee could
rewrite the days or re-send the poll.

### The refusals say different amounts, on purpose

A stranger must not learn the poll is real. Somebody who is on the poll already knows,
so refusing them by name costs nothing and explains everything.

So the read path returns `Optional.empty()` for a stranger and the write path throws
the same `IllegalArgumentException`, with the same wording, that an id nobody issued
throws. The screen is the existing not-found screen with its existing copy, and that
copy deliberately does **not** mention invitee lists: "you are not on the list"
confirms there is a list, which confirms the poll.
`PollJourneyTest.aStrangerCannotTellThePollExists` compares the two screens as text and
fails if they ever diverge.

`requireOrganizer` therefore has three answers rather than two — organizer proceeds,
invitee gets `NotTheOrganizerException` naming the poll and the address, anybody else
gets the unknown-id refusal.

Getting that ordering wrong is easy, and writing the tests found two places where it
already was:

- `requireOrganizer` loaded the poll before asking who was calling, so **a stranger
  calling `lock` was refused by name**, which told her the poll existed.
- `castVote` checked that the poll was open *before* checking the caller was invited,
  so **a stranger voting on a closed poll was told it had closed.** Invitation now
  comes first, and `requireOpen` takes a poll rather than an id so it cannot be called
  any earlier.

---

## 3. Who a poll goes to

There is no team and no directory. The only way anybody gets onto a poll is the
organizer typing their email address.

`AccountDirectory` has no method that hands a screen everybody. `matching` is the only
way in and it answers nothing at all below three characters, so nobody is listed until
the organizer has typed enough to have known who they were looking for. `forInvite`
turns an address with no account behind it into somebody who can still be invited.

The rules, from the design's own implementation notes and followed as written:

- **Three characters minimum**, debounced 250ms (`ValueChangeMode.LAZY` with a
  matching timeout).
- **Match a whole address as typed, or the start of any part of its local part** — so
  `sar` finds both `sara.naslund@acme.com` and `t.sarkar@acme.com`.
- **At most five rows**, because a long answer is a directory too.
- **The organizer never appears as a match**, and is added implicitly.
- **Anything else resolves to an email invite** rather than to an error.

**Where this tightened the spec.** The design says matches come from "email prefix or
any dot/at-delimited part". Read literally that includes the domain, and a search for
`acme` would hand five colleagues to anybody who guessed a company name — the listing
the three-character rule exists to prevent. So `AccountDirectory` matches the local
part only, and `doesNotMatchTheDomain` pins it.

The other half of the design's rule — "only accounts that share a workspace or a past
poll with you" — has no workspace to filter against, because there is no notion of
one. What the directory does support is the distinction the rows show, so
`InviteeSearch` counts the polls two people have actually both answered and the matches
with history sort first.

**Searching for yourself.** The organizer is never a match, which is the design's rule
and the right one — but a query only they answer to then comes back empty and reads as
"nobody by that name", as if their own account did not exist. Typing `tom` as Tom Beck
was exactly that. `matchesSearcher` answers whether the query was reaching for the
searcher's own account, and the field says "That's you — you decide either way"
instead. It is not a leak: the only address it confirms is the one the searcher already
typed and already owns.

**Match ordering is by address**, not by who signed in first. Sign-in order was what a
`LinkedHashMap` happened to give, and it decided which five of six matches came back —
a tie-break nobody chose. Alphabetical is at least the same answer twice.

**Two screens, not an overlay.** Naming a poll and choosing who decides it are separate
screens: the search needs a keyboard, a result list and a growing set of chips at the
same time, and on a phone a dialog gives all three the same few hundred pixels. Both
write to `PollDraft`, held by the session-scoped presenter, so stepping out to search
and coming back loses nothing and abandoning the flow writes no half-built poll into
the store.

---

## 4. What identifies a poll

`UUID.randomUUID()`, generated on create, typed as a `UUID` from the record through the
service to the route parameter. The voting link is
`/vote/3f2a1c8e-5b9d-4e7a-8c6f-1d2e3a4b5c6d`.

It used to be made from the title — "Q3 team offsite" became `q3-team-offsite`, with a
counter appended on collision. It read beautifully and was wrong three ways:

- **Two polls may legitimately share a name.** Teams ask about "Team event" every few
  months. The second got `team-event-1`, which is not a name anybody would choose and
  is one somebody else's poll could already be using.
- **The counter could not survive a restart.** It was a per-instance `AtomicInteger`
  shared across every title, so it restarted at zero while the polls did not: with
  `q4-review` and `q4-review-1` stored, the next "Q4 review" produced `q4-review-1`
  again, and the store was a map, so `put` **silently overwrote the existing poll.**
  Harmless only because nothing survived a restart; durable data loss the moment the
  polls did.
- **A readable id is a guessable id.** `q3-team-offsite` was a URL a stranger could
  arrive at by typing what a team is obviously called. Section 2 closes that hole
  properly, and the unguessable id is still worth having: it is what keeps the
  *existence* of a poll from being enumerable by anybody, invited or not.

Consequences:

- **The share card truncates the link on a phone.** `.link-url` is `nowrap` with an
  ellipsis, so 36 characters read as `whichday.example.com/vote/3f2a1c8e-5b9…`. The
  Copy button beside it is the affordance that matters, and the link is whole in the
  mail and on a wider screen. A shorter random id would have read better and would have
  meant writing and defending our own generator; a UUID needs no collision argument.
- **A malformed id and an id nobody issued are the same answer.** `PollScreen` is the
  only place a route parameter is read anywhere, so it is the only place that parses
  one, and anything that is not a `UUID` we issued forwards to the not-found screen.
- The calendar file's `UID:` and its download name carry the id rather than a readable
  name. Nothing reads either.

---

## 5. The days on the table

Screen 2 of the design draws September on a Monday-first grid with three kinds of cell:
a hairline-outlined day that can be chosen, an accent-filled day that has been, and a
dimmed day with no outline. `MonthCalendar` draws that grid, with one rule changed.

**Weekends are offered, which the design does not do.** The design dims every Saturday
and Sunday in the same grey as the expired days, and `isSelectable` used to agree. A
day is now selectable when it has not gone yet, is inside the range the caller allows,
and is not already ruled out.

That was the design's rule rather than an oversight of it, and it was still wrong to
keep: a leaving lunch on a Saturday, a weekend offsite, a Sunday kickoff — a tool for
asking a group which days work has no business deciding that two of the seven are not
days, least of all the two most likely to be the answer for anything social.
`theCalendarOffersWeekends` pins it, and pins that a day already gone is still refused;
the two used to be one predicate and it would be easy to lose the second while changing
the first.

**Monday first, in every locale.** `WeekFields.of(locale)` would start the week on
Sunday in `en-US`, splitting Saturday and Sunday to opposite ends of the grid. It is
the grid the design draws, and a week reads as five days and then the weekend whether
or not the weekend can be chosen. Weekday labels and month names still come from the
locale.

**Whole weeks, always** — four, five or six rows, only as many as the month needs.
Counting to a fixed six rows and stopping when the days ran out was wrong twice over:
November 2026 starts on a Sunday, so its 36th cell landed alone on a sixth row, and
February 2027 is exactly four weeks, so a fixed grid trailed a whole spare week of
March. `theCalendarGridIsAlwaysWholeWeeks` checks both shapes and the months either
side.

**At most three, where a cap applies.** `setMaximumSelection` caps a selection: at the
cap the unchosen days stop offering themselves and the chosen ones stay live, so the
ceiling shows in the grid instead of arriving as a rejection, and swapping one day for
another is two taps and no error. The counter-proposal screen sets it to three — what
the poster row holds across a phone, and the point past which a counter-proposal stops
being one. Reaching it folds the calendar away, because the decision is finished; below
it a "Done" line closes the calendar for whoever finished early.

Enabling and disabling happens **in place**, for the same reason toggling does: the
grid is still holding the button the reader just pressed. Rebuilding the month would
discard it, and with it the caret of anybody selecting days from the keyboard.

**Unavailable days.** `setUnavailable` takes days the calendar must not offer whatever
the rules above say. One caller: the counter-proposal screen passes the poll's own
candidate days, because a day already on the table is not an alternative to it.

**Month navigation is an addition.** The design shows "September 2026" and no way to
leave September, which makes a poll whose days straddle a month boundary impossible to
build — and with everything anchored to the clock that is the common case rather than
the edge one. Two arrows sit beside the year, at the baseline of the month name so they
do not compete with it.

---

## 6. When voting closes

A poll carries a closing **date**, not a moment: `LocalDate closesOn`. Voting is over
from the day after — answer on the closing date and it counts, answer the next morning
and it does not. Whole days, like every other date here.

The organizer chooses it. The share screen's note is the control, and tapping *Change*
reveals a calendar inline — the same disclosure the counter-proposal screen uses.

**The default is the last day on the table, that day included.** It used to be the last
working day before the *first* day, on the reasoning that answering about a day already
gone is pointless, which the design's own copy supported. That reasoning was wrong
about the poll as a whole: with five days offered, the first one passing says nothing
about the other four, and a team that has not decided yet is exactly the team that
still needs to. Days that have passed drop out on their own — the ballot will not offer
one — so the poll narrows as it goes instead of dying at its first deadline.

`closeOn` clamps whatever it is given into the range a closing date can usefully sit
in: never in the past, never past the last day on the table. The calendar is bounded to
match, so the clamp is a backstop rather than the thing the organizer meets. Where the
two bounds cannot both hold — the last option is today or tomorrow — the last day on
the table wins.

### What this replaced, and why it was wrong

`today + 5 days`, rolled forward to the next-or-same Friday, at 18:00. One screenshot
of the share screen showed all three faults at once:

- **The window depended on the weekday it was created.** Five days out on a Sunday,
  eleven on a Monday. Nothing about a poll changes because of when it was made.
- **It ignored the days being voted on**, so it could close *after* them: candidate
  days from the 24th, voting open until the 28th.
- **The share screen showed the current clock.** A poll not yet sent has no closing
  date and the fallback was `now()`, which is why the note read "Thursday 3:37 PM". A
  minute value in a product with no minutes was the tell.

### Closing has to mean something

A label that said closed while answers still landed would be worse than no label.
`requireOpen` refuses a vote or a decline unless the poll is `OPEN`, so a stale tab
cannot post one after the date has passed — and a poll that was never sent refuses
answers too. The ballot and the counter-proposal screen forward away rather than
offering a control that would fail, and the results screen drops the nudge and the
your-turn prompt, because neither is anybody's move any more.

`PollState` carries `CLOSED` for the moment voting is over with no day locked in. That
is the organizer's cue: the poll list marks it, and the results screen shows the
standings with only the lock left to do.

**The state is derived on every read, never stored** — a pure function of the locked
day, whether there are candidate days, the closing date and the clock. A stored column
would be wrong from the moment the clock crossed the closing date with nobody writing
to the row, and the thing that would fix that is a scheduled sweep
([issue 0005](issues/0005-closing-happens-on-read.md)). Adding the column later is a
migration and a backfill; removing one that lied for a month is not.

Seeding is the one path around the guard: `PollService.record` is package-private and
only `castVote` and the test fixtures reach it, to build polls that were decided before
the application started — history the public path is right to refuse.

---

## 7. Answering

**Everybody invited answers, the organizer included.** The design gives the organizer
the results screen and gives voting to everybody else, which leaves the one person who
called the meeting unable to say which days work for them — while still being invited
and still counted in every denominator. The symptom was the results screen offering the
organizer a nudge to themselves.

Both halves are fixed: `Poll.awaitingOthers` excludes the person looking, so a nudge
only ever names somebody else, and the results screen carries a card for the viewer's
own answer — "You haven't picked your days yet" with a way to the ballot, or "You said
yes to N days" with a way to change them. Submitting returns the organizer to the
standings they came from rather than to a voter's receipt.

The organizer's vote is **explicit, not implicit.** Counting every candidate day as a
yes on their behalf would be the other way to close the gap and it would be wrong: an
organizer puts days on the table to find out what the team can do, and often cannot do
all of them themselves. Their availability is a real answer, so it is one they give.

**Saying none of them work is always available.** It moves the counts and empties the
waiting list, and without it the poll has no way for somebody to answer it truthfully.
A counter-proposal is recorded against the ballot rather than added to the candidate
days: it becomes a column only if the organizer accepts it.

**The organizer decides whether other days may be put forward.** Not in the design,
which shows the counter-proposal screen as always available. It is a real question for
a poll whose days are fixed by something outside it — a booked room, a visitor's only
free week — where inviting alternatives just collects answers nobody can act on. The
switch sits on the candidate-days screen, because it is a rule about those days and the
organizer is looking at them. With it off, that screen keeps its confirmation and its
note field and loses only the calendar, and the footer stops caveating a proposal that
cannot be made.

**Replacing the candidate days drops votes for days no longer on the table.** A tally
for a withdrawn day would otherwise keep being counted. It is the one operation where a
partial update would be observable, which is why it is transactional and why the row
lock makes it mutually exclusive with a vote arriving on the same poll.

---

## 8. Drafts

A poll exists from the moment it is named, so leaving the create flow half-finished
leaves a `DRAFT` behind. Those are listed on their own, between the polls that are out
with the team and the settled ones — a draft is further along than nothing and further
back than sent, and the list reads in that order.

**Only the person who named it sees it**, in the list and by direct link. That second
half was missing for a while: the visibility query is "organizer or invitee" and
carries no state, so an invitee holding a draft's id could read it. It only ever
mattered in theory, because the id is unguessable and sending the link is what stops it
being a draft — but a rule stated twice and enforced once is a rule waiting to be
wrong. The state is derived rather than stored, so `stateOf` decides it in Java rather
than JPQL restating it.

Drafts take the quiet list shape the settled polls use rather than a card, because a
draft is not something to answer and should not look like something to answer. Two
actions: **Edit**, which resumes at the day picker, and **Delete**.

**What this replaced.** Drafts were mixed into the main list. An abandoned one showed a
dash where a date goes, read "0 days on the table", and the headline counted it — "3
polls need you" when only one did. `openPolls` now excludes them, which fixes the count
as a consequence rather than as a special case.

**Deleting asks on the row.** Tapping Delete turns the row into "Delete this draft? ·
Keep · Delete". No overlay, because the question is about one line and is short enough
to ask there; no undo, because the confirmation is what makes the tap safe.

**Only a draft can be deleted.** `deleteDraft` refuses anything sent: a live poll has
answers in it and people waiting on it, and discarding one is a decision this does not
make. What that leaves is a sent poll nobody can call off —
[issue 0009](issues/0009-a-sent-poll-cannot-be-called-off.md).

---

## 9. Where the data lives

An H2 database opened as a **file**: one file under `./data` locally, `/app/data` in
the container. Flyway owns the schema (`db/migration/V*.sql`) and
`spring.jpa.hibernate.ddl-auto=validate` fails startup if the entities and the
migrations disagree, rather than quietly rewriting the schema to match.

Nothing seeds it. The first person to sign in gets an empty list.

Reads build immutable records on the spot. `StoredPoll`, `StoredBallot` and
`StoredAccount` are entities, and they are package-private and never returned, so a
screen holding a `Poll` holds a snapshot rather than a window into the store. That is
why no view changed when this stopped being a map in memory.

### Not PostgreSQL, which §10 asks for

`CODING_CONVENTIONS.md` §10 says PostgreSQL through Spring Data JPA. This deviates on
the engine and on nothing else.

The whole product is one container somebody self-hosts from Docker Hub. A second
container, plus a network, plus credentials, plus a backup story, is a much larger
change to the *deployment* than persistence is to the code — and it would be asking
every reader of the README to run a database server so that seven people can pick a
Thursday.

`MODE=PostgreSQL` and Flyway-owned migrations keep that from being a one-way door: `V1`
is portable SQL a real PostgreSQL runs unchanged, so moving is a URL and a dependency
rather than a rewrite. The two places the engine shows through are named where they
happen — `offered_day` avoids H2's reserved `day`, and the timestamp columns declare
precision 6 because H2 keeps nanoseconds where PostgreSQL truncates to microseconds,
and a test that passed only on H2's extra precision would be encoding the deviation
instead of hiding from it.

### Consequences

- **One process, one file.** An embedded H2 file can only be opened by the process
  holding it, so two containers on one volume means the second will not start. Nothing
  enforces that, which is why the README says it out loud. It is also what makes coarse
  locking honest: there is exactly one writer.
- **The file is the backup unit.** Copy `whichday.mv.db` while the app is stopped.
- **`synchronized` came off every service method.** A monitor inside a transactional
  proxy is acquired after the transaction opens and released before it commits, so it
  reads like a guarantee and is not one. Writers take a `PESSIMISTIC_WRITE` row lock on
  the one poll they are changing, held until commit; readers take none, and every
  writer locks exactly one row so there is no order to deadlock over.
- **`created_at` exists to hold list order.** The in-memory store was insertion-ordered
  and the three list screens rendered it directly, so without the column the lists would
  silently reorder to whatever the database handed back.
- **Ordering that is load-bearing is held explicitly.** Invitees carry an `ordinal`
  because the organizer leads the list and the avatar stacks and tallies read that
  order; candidate days are a sorted set, which is the order every write already
  produced.
- Flyway warns at startup that H2 2.4.240 is newer than the version it was verified
  against. Both versions are Boot's managed ones, so the pairing is Boot's rather than
  ours.

---

## 10. How a person is stored

A poll row, an invitation and a ballot identify a person by **email address and nothing
else**. A single `account (email primary key, name)` table is the one place a name
lives, written when somebody signs in and never seeded.

A `Person` handed to a screen is assembled on read: the name from `account` if there is
a row, `Person.outsider(address)` if there is not. `avatarTone` is recomputed from the
address rather than stored — both `Person` factories already derive it that way, so a
column would only give a stored tone the chance to disagree with a computed one.

### Why

**An invitee may have no account at all.** Somebody invited by email who has never
signed in is a person this application fully supports, so there is no account id for a
foreign key to point at, and `poll_invitee.email` deliberately has no foreign key to
`account.email`.

**A copy per row is a name that goes stale in some places and not others.** The
alternative was `email`, `name` and `avatar_tone` on every invitee row and every ballot
row, which is what the in-memory store held — and it had a live bug in it.
`Poll.awaiting()` and `Poll.ballotOf()` compare whole `Person` records, so somebody
invited before they had an account was `("bob@example.com", "")` in the invite list and
`("bob@example.com", "Bob Smith")` on their own ballot once they signed in and voted:
two records that are not equal, so **a person who had answered read as still awaiting.**
Transient only because nothing survived a restart; persisting the copies would have made
it permanent.

Assembling from one row per address closes it by construction — every mention of an
address inside one snapshot comes from the same lookup, so the records are equal.

### Consequences

- **A late signup gets its name.** An address invited before its owner ever signed in
  showed as an address forever; now it shows their name from their first sign-in onward,
  on polls that predate the account.
- **A corrected name is corrected everywhere**, because there is one copy.
- **`draftPolls` got more correct.** Its "only mine" filter is an address match in the
  query, where it used to compare whole people — so a draft whose author has since
  corrected their name is no longer invisible to its own author.
- `PollService` takes a `PersonLookup` rather than the whole `AccountDirectory`: it
  turns addresses into people and has no business remembering anybody or searching. One
  bulk lookup serves a whole list screen, so the account table is read once per screen
  rather than once per person.
- **Every write normalises the address**, because the address is the identity now.
  `Person.outsider` does not normalise its argument, so without that a person built
  from a mixed-case address would be stored as a row nothing could find again.
- A test fixture that votes as people with accounts has to record the accounts first.
  `Sample.signedInBefore` is that step, and a poll fixture without it has a team of
  nameless addresses — which is correct, and worth one comment in each test.

---

## 11. Where the implementation departs from the design

Everything here is deliberate. Anything not listed is meant to match.

### Left out

**The phone status bar.** Every frame draws `9:41` and a battery. That is the device
the mockup sits in, not the application — a web page drawing a fake system clock is a
lie about what it is. The 390×844 outline goes with it: the column is capped at 30rem
and centred, so it fills a phone and does not stretch across a desktop.

**"Clashes with your calendar."** Screen 4's fifth row is dimmed because the voter's
own calendar says so. There is no calendar integration to ask, and inventing a clash
would be worse than not having one. The dimmed-and-disabled treatment survives and is
used for a candidate day that has already passed, which is a real reason a day cannot
be voted for.

**"Tell me when the date is locked."** The receipt's notify toggle is gone rather than
substituted. It was a pre-ticked checkbox with no listener, read by nothing, so every
voter was told a promise they had not made and that nothing could keep — there is no
mail path at all ([issue 0003](issues/0003-nothing-is-ever-actually-sent.md)). Same
reasoning as the calendar clash: a control that cannot do what it says is worse than no
control. It comes back with the thing that would send the notification, and then the
open question is Vaadin's — there is no switch component, and turning a checkbox into
one means styling into its shadow root.

**The dimmed weekend** — see section 5.

**The wordmark.** The design says "When2"; this is Whichday. It is `app.name` in
`translations.properties`, so it is one key either way.

**Warning chips for a malformed pasted address.** The design draws a bad entry as an
orange chip. Here it stays in the field with the rest of the paste accepted, on the
grounds that an address with a typo in it is worth fixing where it can still be edited
— a chip you cannot correct is a worse dead end than a field you can.

### Substituted

**Message and Add-to-calendar do real work.** The design shows both as buttons and says
nothing about what happens. There is no mail server here, so "Message" is a `mailto:`
the browser hands to whatever the reader actually uses, and "Add to calendar" is a
generated iCalendar file — all-day events, since the whole product is whole days. Both
are anchors wearing the button's clothes. "Copy" uses the clipboard API and says so when
it works.

### Added

Each of these exists because the design's flow is unreachable or unusable without it.

**Counter-proposals reach the organizer.** Screen 2a promises "Ada sees it next to the
counts" and the design never draws that screen. The results view grows a "Proposed
instead" section listing each proposal, with an "Add it" button for the organizer — the
only thing that makes the promise true, and the only thing that makes `acceptProposal`
reachable. Everybody sees what was put forward; only the organizer is offered the
button, because accepting one adds a column to everybody's ballot.

**Invitee chips truncate the address; they do not abbreviate it.** The design's chips
read `miro@…`, cutting the domain. Here the chip holds the whole address and lets CSS
truncate it, with the full one on the tooltip and in the Added list underneath — one
rule instead of a rule plus an abbreviation scheme to get wrong.

**The receipt's standings show every day on the table.** The design draws three bars,
and the voter it draws had said yes to three of five days — so the three bars are as
easily "the days you chose" as "the leading three". Neither truncation survives contact
with a voter who says yes to all five: three bars leave two of their own days undrawn,
with no way to tell whether those are losing or simply not shown, under a heading that
promises where the *team* stands. Every candidate day gets a bar. Which ones are yours
is already answered by the posters above it.

**No dialogs anywhere.** Two screens needed a picker the design does not draw —
choosing who decides, and proposing a day instead — and an overlay was the wrong answer
to both: on a phone a dialog gets the same few hundred pixels as the screen under it,
and it covers the very thing it is filling in. Choosing who decides became its own
screen; proposing a day became an inline calendar folded away behind the design's own
dashed "+", because a month is too tall to sit above the note field permanently but far
too small a decision to spend a screen on. It is the same `MonthCalendar` the organizer
puts days on the table with, so a voter proposing an alternative and an organizer
offering one are the same gesture. Nothing in `src/main` constructs a `Dialog`.

**A way home on every screen.** The design draws a back chevron on three screens and
nothing at all on four others — a voter who lands on a receipt, or an organizer watching
the counts, has no way out. Every screen carries one home affordance marked `nav-home`:
the wordmark where the design already puts one, a home glyph in the top bar otherwise,
and the footer button on the not-found screen. The two wizard screens keep their back
chevron as well, because stepping back to edit the days and leaving the poll altogether
are different intentions and should not be the same tap.
`PollJourneyTest.everyScreenCanLeave` asserts the whole set.

**The results screen has one empty state, not a separate screen.** Screens 5 and 2c are
the same route: dashed rows and a "Waiting on" list while nobody has answered, the
standings once somebody has. Same layout either way, so the poll does not appear to
change shape when the first answer lands.

---

## 12. Testing decisions

**A clock the test can move.** `Clock.fixed` cannot reach tomorrow and the service
holds one clock for its lifetime, so `TestClock` is advanceable and has an origin to
`reset()` to. `closesTheDayAfter` walks to the closing date, asserts the poll is still
open, advances one more day, and asserts it is not.

**One Spring context for the whole suite**, and the tables emptied in `@BeforeEach`
rather than a context rebuilt per method. `@DirtiesContext` used to be the isolation
mechanism, which stopped working the moment the store outlived the context. Not a
`@Transactional` test either: that would keep every entity managed for the whole method,
which is exactly the condition the package-private-entity rule exists to prevent — a
missing `@Transactional` on a service method would pass and then fail in production.

**Flyway runs in the tests too**, against in-memory H2, with `ddl-auto` still
`validate`. `create-drop` would build the schema from the entities and then validate the
entities against it — a tautology that passes for any migration, so the first thing to
notice that `V1` disagreed with an entity would be production startup.

**The sample data lives in the test tree.** It used to be production seeding. Once
signing in became the only way in, an account existed because somebody authenticated —
so a hard-coded Ada Lindqvist had nowhere to live in the application, and the polls she
owned had nobody to own them. `Sample` still builds that shape and still anchors to the
clock rather than writing September 2026 out: the design's numerals would have put the
whole fixture in the past within the year. Six ballots over five days give 6 / 4 / 3 /
2 / 1, with Jonas Wirtanen holding out so "Everyone but Jonas" and the nudge prompt have
something real to say.

**A test that cannot fail is not a test.** Every guard in section 2 was added with a
test, and each was checked by removing the guard and watching it fail — because the
existing suite passed happily both before and after, having never signed in as somebody
uninvited.

---

## 13. Accepted loose ends

- **`V1`'s comments point at two files that no longer exist**, this one among them. They
  stay wrong on purpose: a migration that has run is immutable, and editing even a
  comment changes its checksum — Flyway then refuses to start against any database that
  applied the old bytes. Verified, not assumed: it fails with
  `FlywayValidateException`. Not a trade worth making for a tidier comment.
- **The share card ellipsises the voting link on a phone** — section 4.
- **An alias of an invited address does not match** — section 2.

---

## Known problems

[`issues/`](issues/) — one file each, saying what is wrong, what it costs, and what
fixing it would take. A ticket stays there until it is fixed, and then it goes.
