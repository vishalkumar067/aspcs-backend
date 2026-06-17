-- V10: Make gallery_images width/height nullable
-- Original V4 migration created these columns as NOT NULL
-- but our GalleryModule doesn't track image dimensions.

ALTER TABLE gallery_images
    ALTER COLUMN height DROP NOT NULL,
    ALTER COLUMN width  DROP NOT NULL;
