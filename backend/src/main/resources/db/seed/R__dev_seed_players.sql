-- DEV-ONLY seed (D13). Loaded only by the `local` Spring profile
-- (spring.flyway.locations adds classpath:db/seed). Never add this location in other environments.
--
-- Accounts: alice, bob, carol
-- Dev password for all three: password123
-- Hash: Spring Security DelegatingPasswordEncoder (bcrypt, strength 10).
--
-- Repeatable migration: Flyway re-runs it only when this file changes; "on conflict do nothing" keeps
-- existing rows (and their stats) untouched.
insert into players (username, password_hash, age, gender, games_played, victories, average_score)
values ('alice', '{bcrypt}$2a$10$sQNyndlHMRoKBhrEbaHyouHn3m70mhn2732V3o8s0lR1BruBNJHM.', 25, 'FEMALE', 0, 0, 0),
       ('bob',   '{bcrypt}$2a$10$sQNyndlHMRoKBhrEbaHyouHn3m70mhn2732V3o8s0lR1BruBNJHM.', 30, 'MALE',   0, 0, 0),
       ('carol', '{bcrypt}$2a$10$sQNyndlHMRoKBhrEbaHyouHn3m70mhn2732V3o8s0lR1BruBNJHM.', 28, null,     0, 0, 0)
on conflict do nothing;
