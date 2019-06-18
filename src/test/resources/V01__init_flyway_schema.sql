create schema if not exists other;

create table other.foo
(
    id   UUID primary key,
    data JSONB not null
);