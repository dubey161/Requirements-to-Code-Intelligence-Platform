create table generated_code_bundle (
    id              uuid        primary key,
    requirement_id  uuid        not null references requirement_story (id) on delete cascade,
    generated_files text        not null,
    target_package  varchar(200) not null,
    generated_at    timestamp with time zone not null,
    constraint uq_generated_code_requirement unique (requirement_id)
);

create index idx_generated_code_requirement on generated_code_bundle (requirement_id);
