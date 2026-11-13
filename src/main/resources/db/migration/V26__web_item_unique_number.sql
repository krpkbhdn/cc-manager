-- A duplicated number had its screenshot file overwritten by the later item, so every row of such a group loses the file reference;
-- later duplicates continue after the highest number of their revision.
UPDATE web_review_item SET screenshot_file = NULL
WHERE (revision_id, n) IN (SELECT revision_id, n FROM web_review_item GROUP BY revision_id, n HAVING COUNT(*) > 1);

CREATE TEMP TABLE web_item_renumber AS
SELECT id,
       (SELECT MAX(m.n) FROM web_review_item m WHERE m.revision_id = d.revision_id) AS top,
       ROW_NUMBER() OVER (PARTITION BY revision_id ORDER BY id) AS position
FROM web_review_item d
WHERE id NOT IN (SELECT MIN(id) FROM web_review_item GROUP BY revision_id, n);

UPDATE web_review_item
SET n = (SELECT r.top + r.position FROM web_item_renumber r WHERE r.id = web_review_item.id)
WHERE id IN (SELECT id FROM web_item_renumber);

DROP TABLE web_item_renumber;

CREATE UNIQUE INDEX idx_web_review_item_revision_number ON web_review_item (revision_id, n);
