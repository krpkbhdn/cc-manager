ALTER TABLE web_review_item ADD COLUMN viewport_width INTEGER;
ALTER TABLE web_review_item ADD COLUMN viewport_height INTEGER;
ALTER TABLE web_review_item ADD COLUMN device_name TEXT;
ALTER TABLE web_review_item ADD COLUMN device_category TEXT CHECK (device_category IS NULL OR device_category IN ('MOBILE', 'TABLET', 'DESKTOP'));
