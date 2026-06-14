create table risk_score (
    id                          uuid        primary key,
    requirement_id              uuid        not null references requirement_story (id) on delete cascade,
    pr_number                   integer     not null,
    overall_score               integer     not null check (overall_score between 0 and 100),
    compliance_contribution     integer     not null,
    security_contribution       integer     not null,
    completeness_contribution   integer     not null,
    risk_level                  varchar(20) not null,
    recommendation              text        not null,
    scored_at                   timestamp with time zone not null,
    constraint uq_risk_score_requirement unique (requirement_id)
);

create index idx_risk_score_requirement on risk_score (requirement_id);
