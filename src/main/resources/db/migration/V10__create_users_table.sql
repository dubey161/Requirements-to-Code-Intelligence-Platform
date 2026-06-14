create table users (
    id         uuid         primary key,
    email      varchar(255) not null,
    password   varchar(255) not null,
    role       varchar(30)  not null,   -- ADMIN | ENGINEERING_MANAGER | DEVELOPER
    active     boolean      not null default true,
    created_at timestamp with time zone not null
);

create unique index uq_users_email on users (email);
