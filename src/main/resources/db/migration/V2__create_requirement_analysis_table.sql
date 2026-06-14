create table requirement_analysis (
    id                uuid        primary key,
    requirement_id    uuid        not null references requirement_story (id) on delete cascade,
    knowledge_model   text        not null,
    analyzer_version  varchar(50) not null,
    analyzed_at       timestamp with time zone not null,
    constraint uq_requirement_analysis_requirement unique (requirement_id)
);

create index idx_requirement_analysis_requirement
    on requirement_analysis (requirement_id);
