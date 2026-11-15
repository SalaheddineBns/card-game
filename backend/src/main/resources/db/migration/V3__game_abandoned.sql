-- D25: games left IN_PROGRESS by a restart (live state lost) are marked ABANDONED at startup.
alter table games drop constraint ck_games_status;
alter table games add constraint ck_games_status check (status in ('IN_PROGRESS', 'FINISHED', 'ABANDONED'));
alter table games drop constraint ck_games_finished_at;
alter table games add constraint ck_games_finished_at
    check ((status in ('FINISHED', 'ABANDONED')) = (finished_at is not null));
