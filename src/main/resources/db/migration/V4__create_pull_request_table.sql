create table pull_request (
    id              uuid        primary key,
    requirement_id  uuid        not null references requirement_story (id) on delete cascade,
    pr_number       integer     not null,
    html_url        varchar(500) not null,
    head_branch     varchar(200) not null,
    head_sha        varchar(100) not null,
    created_at      timestamp with time zone not null,
    constraint uq_pull_request_requirement unique (requirement_id)
);

create index idx_pull_request_requirement on pull_request (requirement_id);
