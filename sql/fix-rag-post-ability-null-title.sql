START TRANSACTION;

UPDATE rag_knowledge_document d
JOIN post_ability_model pam ON pam.id = d.source_ref_id
SET d.title = CONCAT(SUBSTRING_INDEX(d.title, CHAR(32, 45, 32), 1), CHAR(32, 45, 32), pam.ability_name),
    d.content = REPLACE(d.content, CONCAT(CHAR(35), CHAR(110), CHAR(117), CHAR(108), CHAR(108)), pam.ability_name),
    d.content_revision = d.content_revision + 1,
    d.indexed_revision = 0,
    d.indexing_status = 'PENDING',
    d.indexing_error = NULL
WHERE d.source_type = 0x504F53545F4142494C4954595F4D4F44454C
  AND d.title LIKE CONCAT(CHAR(37), CHAR(35), CHAR(110), CHAR(117), CHAR(108), CHAR(108), CHAR(37))
  AND d.is_deleted = 0
  AND pam.ability_name IS NOT NULL
  AND TRIM(pam.ability_name) <> CHAR(0);

UPDATE rag_knowledge_document d
LEFT JOIN post_ability_model pam ON pam.id = d.source_ref_id
SET d.is_deleted = 1,
    d.doc_status = 'ARCHIVED',
    d.indexing_status = 'SKIPPED',
    d.indexing_error = 'Archived: post ability has no display name',
    d.updated_time = NOW()
WHERE d.source_type = 0x504F53545F4142494C4954595F4D4F44454C
  AND d.title LIKE CONCAT(CHAR(37), CHAR(35), CHAR(110), CHAR(117), CHAR(108), CHAR(108), CHAR(37))
  AND d.is_deleted = 0
  AND (pam.id IS NULL OR pam.ability_name IS NULL OR TRIM(pam.ability_name) = CHAR(0));

COMMIT;

SELECT COUNT(*) AS remaining_active_null_titles
FROM rag_knowledge_document
WHERE source_type = 0x504F53545F4142494C4954595F4D4F44454C
  AND title LIKE CONCAT(CHAR(37), CHAR(35), CHAR(110), CHAR(117), CHAR(108), CHAR(108), CHAR(37))
  AND is_deleted = 0;
