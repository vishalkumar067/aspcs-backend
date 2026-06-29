-- V9: Add missing columns to gallery tables
-- The original V4 migration didn't have all columns that GalleryModule.java expects.

-- gallery_images: add created_at and other missing columns
ALTER TABLE gallery_images
    ADD COLUMN IF NOT EXISTS created_at     TIMESTAMP,
    ADD COLUMN IF NOT EXISTS thumbnail_url  VARCHAR(1000),
    ADD COLUMN IF NOT EXISTS public_id      VARCHAR(500),
    ADD COLUMN IF NOT EXISTS alt            VARCHAR(500);

-- gallery_albums: add missing columns
ALTER TABLE gallery_albums
    ADD COLUMN IF NOT EXISTS updated_at     TIMESTAMP,
    ADD COLUMN IF NOT EXISTS event_date     DATE,
    ADD COLUMN IF NOT EXISTS cover_image_url VARCHAR(1000);

-- Back-fill created_at for existing rows
UPDATE gallery_images SET created_at = NOW() WHERE created_at IS NULL;
UPDATE gallery_albums SET updated_at = NOW() WHERE updated_at IS NULL;
