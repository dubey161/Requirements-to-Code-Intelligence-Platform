create table compliance_report (
    id                uuid    primary key,
    requirement_id    uuid    not null references requirement_story (id) on delete cascade,
    pr_number         integer not null,
    gaps              text    not null,
    compliance_score  integer not null check (compliance_score between 0 and 100),
    checked_at        timestamp with time zone not null,
    constraint uq_compliance_report_requirement unique (requirement_id)
);

create index idx_compliance_report_requirement on compliance_report (requirement_id);
