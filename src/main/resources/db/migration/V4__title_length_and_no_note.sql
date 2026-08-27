-- Two changes with one reason: a column should say what the field that fills it says.
--
-- poll.title was unbounded because the field was, and both ends were the wrong way round
-- (docs/issues/0013). The field now stops at fifty characters, so the column does too.
-- The update is what makes the narrowing safe on a database that has been running with no
-- limit at all: PostgreSQL refuses the type change while a longer value exists, and H2
-- would truncate silently. Doing it out loud is better than either.
update poll set title = substr(title, 1, 50) where length(title) > 50;

alter table poll alter column title set data type varchar(50);

-- ballot.note goes entirely. The screen collected it under the label "Note to the team"
-- and no screen ever showed it to the team or to anybody else, so the column held text
-- nobody could read — see docs/REQUIREMENTS.md §7. A day put forward says the same thing
-- and is something the organizer can act on.
alter table ballot drop column note;

-- account.name is deliberately left at 255. The name a visitor types is capped at twenty
-- by the field that takes it, but login mode does not take it from a field at all: it
-- arrives in the id token, and a provider that sends a longer full name would otherwise
-- turn a valid sign-in into a failed insert.
