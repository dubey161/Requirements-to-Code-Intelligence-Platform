-- Nullable so existing requirements are not broken by the migration.
-- New requirements will have created_by set to the authenticated user's id.
alter table requirement_story
    add column created_by uuid references users (id) on delete set null;

create index idx_requirement_story_created_by on requirement_story (created_by);
