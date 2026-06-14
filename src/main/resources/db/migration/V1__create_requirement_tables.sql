create table requirement_story (
    id uuid primary key,
    external_key varchar(100) not null unique,
    title varchar(300) not null,
    description text not null,
    status varchar(30) not null,
    created_at timestamp with time zone not null,
    updated_at timestamp with time zone not null,
    version bigint not null default 0
);

create table requirement_acceptance_criterion (
    requirement_id uuid not null references requirement_story (id) on delete cascade,
    criterion_order integer not null,
    criterion text not null
);

create index idx_requirement_acceptance_criterion_requirement
    on requirement_acceptance_criterion (requirement_id);

alter table requirement_acceptance_criterion
    add constraint uq_requirement_acceptance_criterion_order
    unique (requirement_id, criterion_order);
