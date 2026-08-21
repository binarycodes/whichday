-- Whichday's whole store. Portable on purpose: this runs unchanged against
-- PostgreSQL, and H2 reads it in MODE=PostgreSQL (CODING_CONVENTIONS.md §10).
--
-- A person is an email address here and nothing else. The one place a name lives is
-- the account table, so an invitee who has never signed in needs no row at all and
-- one who signs in later is named everywhere at once — there is only one copy to
-- change. See docs/clarifications/0011-a-poll-stores-addresses.md.

create table account (
    email varchar(320) not null,
    name  varchar(255) not null,
    constraint pk_account primary key (email)
);

create table poll (
    id                   uuid                        not null,
    title                varchar                     not null,
    organizer_email      varchar(320)                not null,
    closes_on            date,
    locked_day           date,
    opened_at            timestamp(6) with time zone,
    -- What the three list screens are ordered by. The in-memory store was insertion
    -- ordered and the screens rendered it directly, so without this column the lists
    -- would silently reorder to whatever the database hands back.
    created_at           timestamp(6) with time zone not null,
    alternatives_allowed boolean                     not null,
    constraint pk_poll primary key (id)
);

-- The two predicates a poll can be scoped by, indexed here rather than in the
-- migration that will need them: docs/issues/0002-any-signed-in-user-can-read-any-poll.md.
create index idx_poll_organizer on poll (organizer_email);

-- Invitee order is load-bearing: the organizer leads the list, the rest keep the
-- order they were added in, and the avatar stacks and the tallies both read it.
create table poll_invitee (
    poll_id uuid         not null,
    ordinal integer      not null,
    email   varchar(320) not null,
    constraint pk_poll_invitee primary key (poll_id, ordinal),
    constraint uq_poll_invitee_email unique (poll_id, email),
    constraint fk_poll_invitee_poll foreign key (poll_id)
        references poll (id) on delete cascade
);

create index idx_poll_invitee_email on poll_invitee (email);

-- offered_day rather than day: DAY is a reserved word in H2 and not in PostgreSQL,
-- and a name that needs no quoting on either engine is worth more than the shorter one.
create table candidate_day (
    poll_id     uuid not null,
    offered_day date not null,
    constraint pk_candidate_day primary key (poll_id, offered_day),
    constraint fk_candidate_day_poll foreign key (poll_id)
        references poll (id) on delete cascade
);

create table ballot (
    poll_id     uuid         not null,
    voter_email varchar(320) not null,
    note        varchar,
    constraint pk_ballot primary key (poll_id, voter_email),
    constraint fk_ballot_poll foreign key (poll_id)
        references poll (id) on delete cascade
);

create index idx_ballot_voter on ballot (voter_email);

create table ballot_day (
    poll_id     uuid         not null,
    voter_email varchar(320) not null,
    chosen_day  date         not null,
    constraint pk_ballot_day primary key (poll_id, voter_email, chosen_day),
    constraint fk_ballot_day_ballot foreign key (poll_id, voter_email)
        references ballot (poll_id, voter_email) on delete cascade
);

-- Keyed by the slot rather than by the day: these come from the voter as a list, and
-- proposing the same day twice should not fail an insert.
create table ballot_proposal (
    poll_id      uuid         not null,
    voter_email  varchar(320) not null,
    ordinal      integer      not null,
    proposed_day date         not null,
    constraint pk_ballot_proposal primary key (poll_id, voter_email, ordinal),
    constraint fk_ballot_proposal_ballot foreign key (poll_id, voter_email)
        references ballot (poll_id, voter_email) on delete cascade
);
