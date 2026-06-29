-- V8: Fix gallery_albums category constraint
-- The original constraint only allowed a small set of values.
-- Drop it and replace with the full set used by the admin panel.

ALTER TABLE gallery_albums
    DROP CONSTRAINT IF EXISTS gallery_albums_category_check;

ALTER TABLE gallery_albums
    ADD CONSTRAINT gallery_albums_category_check
    CHECK (category IN (
        'EVENTS',
        'SPORTS',
        'CULTURAL',
        'ACADEMIC',
        'INFRASTRUCTURE',
        'RELIGIOUS',
        'OTHER',
        'GENERAL'
    ));
