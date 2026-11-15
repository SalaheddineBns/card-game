---
name: flyway-migrations
description: Rules for writing Flyway SQL migrations for the card game's PostgreSQL database. Covers file naming, immutability, Postgres types, constraints, indexes, and seed data. Use whenever the database schema or reference data changes.
---

# Flyway migrations (PostgreSQL)

## Where and how

- Folder: `backend/src/main/resources/db/migration/`.
- Versioned files: `V{n}__{snake_case_description}.sql`, e.g. `V1__init_schema.sql`, `V2__add_round_timer.sql`.
  Number them in sequence. Check the highest existing version first.
- Repeatable files: `R__{description}.sql`, for views and functions only.
- Dev-only seed data: `backend/src/main/resources/db/seed/` with `spring.flyway.locations` pointing at it
  in the `local` profile only. Never in production locations.
- Hibernate never touches the schema: `spring.jpa.hibernate.ddl-auto=validate`.

## Golden rules

1. **Never edit a migration that has already been applied** anywhere (it's merged, or it ran on someone's DB).
   Write a new one.
2. One logical change per migration. Name it after the intent, not the ticket.
3. Migrations must work on an empty database **and** on the current schema. Test both by running
   the Testcontainers integration tests.
4. Destructive changes (dropping a column or table, narrowing a type) go in two steps: stop using the
   column in code, deploy, then drop it in a later migration.
5. Never put credentials or environment-specific values in a migration.

## Postgres conventions

| Use | Not |
|-----|-----|
| `bigint generated always as identity primary key` | `serial` |
| `text` or `varchar(n)` when there is a real limit | `char(n)` |
| `timestamptz` | `timestamp` |
| `boolean` | `smallint` flags |
| `varchar` + `check (col in (...))` for enums | Postgres `enum` types (painful to change) |
| `numeric(p,s)` for exact values | `float`/`double` for scores that must be exact |

- Table names: plural snake_case (`players`, `games`, `rounds`, `cards`, `participations`).
- Column names: snake_case. Foreign keys: `<entity>_id`.
- Name every constraint: `pk_players`, `fk_participations_player`, `uq_players_username`, `ck_cards_color`.
- Every foreign key column gets an index (Postgres doesn't create them automatically):
  `create index ix_participations_player_id on participations (player_id);`
- Say what happens on delete explicitly (`on delete cascade` / `restrict`). Don't rely on the default.
- `not null` is the default choice. Make a column nullable only when absence is meaningful.
- Add `created_at timestamptz not null default now()` to tables whose rows are created by users.

## Example

```sql
-- V1__init_schema.sql
create table players (
    id            bigint generated always as identity,
    username      varchar(50)  not null,
    password_hash varchar(100) not null,
    created_at    timestamptz  not null default now(),
    constraint pk_players primary key (id),
    constraint uq_players_username unique (username)
);

create table games (
    id         bigint generated always as identity,
    status     varchar(20) not null,
    created_at timestamptz not null default now(),
    constraint pk_games primary key (id),
    constraint ck_games_status check (status in ('WAITING', 'IN_PROGRESS', 'FINISHED'))
);
```

## Mapping back to JPA

- The entity must match the migration exactly, because `validate` fails at startup otherwise.
- `@Table(name = "players")` and `@Column(name = "password_hash")`. Leave naming to Spring's default
  snake_case strategy, but be explicit when in doubt.
- When a migration renames something, change the entity in the same commit.

## Checklist before finishing

- [ ] New file, next version number, clear name
- [ ] Constraints named, foreign keys indexed, on-delete behaviour explicit
- [ ] Entity updated to match
- [ ] `./mvnw verify` passes (Testcontainers runs every migration from scratch)
