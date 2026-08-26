-- When an account was first seen, which is what the retention sweep needs to know
-- before it drops an anonymous one (docs/REQUIREMENTS.md §9).
--
-- First seen and not last seen: `remember` runs on every read of who is looking, and a
-- column that moved each time would keep a session's row alive for as long as somebody
-- kept the tab open. The entity therefore declares it not updatable.
--
-- Rows that already exist are stamped with the moment this runs. That is the only
-- honest answer available — nothing recorded when they arrived — and it means an
-- anonymous name already in the table gets its full window from here rather than being
-- swept the first time the sweep runs.
alter table account add column created_at timestamp(6) with time zone;

update account set created_at = current_timestamp where created_at is null;

alter table account alter column created_at set not null;
