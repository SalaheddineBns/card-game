-- D22: the games row is created when the game starts (its id is the game id) and finished at the end.
alter table games rename column played_at to started_at;
alter index ix_games_played_at rename to ix_games_started_at;
alter table games add column status varchar(12) not null default 'IN_PROGRESS';
alter table games add column finished_at timestamptz;
update games set status = 'FINISHED', finished_at = started_at;   -- rows written before V2 were finished games
alter table games add constraint ck_games_status check (status in ('IN_PROGRESS', 'FINISHED'));
alter table games add constraint ck_games_finished_at check ((status = 'FINISHED') = (finished_at is not null));
