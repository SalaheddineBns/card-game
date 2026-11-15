-- Initial schema (plan row 15).
-- D9: no combinations column. D11: optional gender enum. D12: no cards/rounds tables, no click ratios or
-- click counts. D18: usernames 3-20 chars [A-Za-z0-9_-], unique ignoring case.

create table players (
    id            bigint generated always as identity,
    username      varchar(20)      not null,
    password_hash varchar(100)     not null,
    age           integer          not null,
    gender        varchar(10),
    games_played  integer          not null default 0,
    victories     integer          not null default 0,
    average_score double precision not null default 0,
    created_at    timestamptz      not null default now(),
    constraint pk_players primary key (id),
    constraint ck_players_username check (username ~ '^[A-Za-z0-9_-]{3,20}$'),
    constraint ck_players_age check (age >= 1),
    constraint ck_players_gender check (gender in ('MALE', 'FEMALE', 'OTHER')),
    constraint ck_players_games_played check (games_played >= 0),
    constraint ck_players_victories check (victories >= 0 and victories <= games_played),
    constraint ck_players_average_score check (average_score >= 0)
);

-- D18: "Alice" and "alice" are the same account.
create unique index uq_players_username_lower on players (lower(username));

-- One row per finished game (D6).
create table games (
    id            bigint generated always as identity,
    played_at     timestamptz not null,
    total_rounds  integer     not null,
    round_seconds integer     not null,
    game_mode     varchar(20) not null default 'Normal',
    constraint pk_games primary key (id),
    constraint ck_games_total_rounds check (total_rounds between 1 and 20),
    constraint ck_games_round_seconds check (round_seconds between 5 and 120),
    constraint ck_games_game_mode check (game_mode in ('Normal'))
);

create index ix_games_played_at on games (played_at);

-- One row per player per game, with the player's final score (D6).
create table participations (
    id        bigint generated always as identity,
    game_id   bigint  not null,
    player_id bigint  not null,
    score     integer not null,
    constraint pk_participations primary key (id),
    constraint fk_participations_game foreign key (game_id) references games (id) on delete cascade,
    constraint fk_participations_player foreign key (player_id) references players (id) on delete restrict,
    constraint uq_participations_game_player unique (game_id, player_id),
    constraint ck_participations_score check (score >= 0)
);

-- game_id is covered by uq_participations_game_player (leading column).
create index ix_participations_player_id on participations (player_id);
