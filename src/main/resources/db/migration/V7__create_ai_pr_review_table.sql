create table ai_pr_review (
    id               uuid         primary key,
    requirement_id   uuid         not null references requirement_story (id) on delete cascade,
    pr_number        integer      not null,
    model_id         varchar(100) not null,
    prompt_version   varchar(20)  not null,
    summary          text         not null,
    approved         boolean      not null default false,
    issues           text         not null,
    raw_response     text,
    reviewed_at      timestamp with time zone not null
);

-- Multiple reviews per requirement (history as models are upgraded)
create index idx_ai_pr_review_requirement on ai_pr_review (requirement_id, reviewed_at desc);
