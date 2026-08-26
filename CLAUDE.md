# Project instructions for Claude

## Coding conventions

**Before writing or editing any code in this repository, read
[`CODING_CONVENTIONS.md`](./CODING_CONVENTIONS.md) and follow every rule in
it.** The conventions cover naming, comments, code structure, Java idioms,
Vaadin, CSS organisation, i18n, persistence, access control and CI.

In particular, recurring expectations:

- Identifiers use full, meaningful words — no single-letter or abbreviated
  names anywhere (variables, parameters, lambda captures, loop indices).
- Add a comment **only** when the code is not self-explanatory; explain
  *why*, never *what*. No section-divider comments.
- Keep view classes thin. Anything with a shape of its own — a calendar, a
  ballot, a row — is its own component class with a focused public API. A
  view builds in `beforeEnter`, never in its constructor.
- No user-facing string literals in code: every one is a key in
  `vaadin-i18n/translations.properties`, resolved through `getTranslation`.
  A label has to be true — a button that navigates does not say "send".
- Colors live in `colors.css` as semantic tokens. Component CSS files
  reference them through `var(--color-…)`; never inline raw hex / rgb /
  named colors.
- Immutable records are the default. JPA entities take **no** Lombok — see
  §4 for why, and for the one exception.
- **Two access modes.** `WHICHDAY_ACCESS_MODE` picks `login` or `anonymous`
  at deploy time, and a change to one screen usually has to answer for
  both. Who may do what is decided in `PollService`, never by which button
  a screen draws.
- Build and test through `./run.sh <task>`. Never invoke `mvn` directly and
  never set `JAVA_HOME` by hand.

If a request would violate a convention, push back and propose the
convention-compliant alternative before proceeding.

## When in doubt

Re-read `CODING_CONVENTIONS.md` first. `docs/REQUIREMENTS.md` records what
the application has to do and why each decision was taken; `docs/issues/`
records what is still wrong. If a situation isn't covered, match the style
of the surrounding code and propose adding the new rule to the conventions
file.
