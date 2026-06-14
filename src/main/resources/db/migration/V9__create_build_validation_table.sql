create table build_validation_report (
    id               uuid    primary key,
    requirement_id   uuid    not null references requirement_story (id) on delete cascade,
    status           varchar(20) not null,   -- PASSED | WARNING | FAILED
    file_count       integer not null default 0,
    error_count      integer not null default 0,
    warning_count    integer not null default 0,
    checks           text    not null,        -- JSON array of check results
    validated_at     timestamp with time zone not null,
    constraint uq_build_validation_requirement unique (requirement_id)
);

create index idx_build_validation_requirement on build_validation_report (requirement_id);
