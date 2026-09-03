ALTER TABLE emp_resume_parse
    ADD COLUMN auto_import TINYINT NULL COMMENT 'Whether this parse automatically submits extracted abilities',
    ADD COLUMN ability_import_status VARCHAR(32) NULL COMMENT 'NOT_REQUESTED/PENDING/SUCCEEDED/REVIEW_REQUIRED/BLOCKED/NO_CLAIMS/FAILED',
    ADD COLUMN ability_import_summary VARCHAR(500) NULL COMMENT 'Automatic or manual import result summary',
    ADD COLUMN ability_imported_at DATETIME NULL COMMENT 'Automatic or manual import completion time';

UPDATE emp_resume_parse
SET auto_import = 0,
    ability_import_status = 'NOT_REQUESTED'
WHERE auto_import IS NULL;

ALTER TABLE emp_resume_parse
    MODIFY COLUMN auto_import TINYINT NOT NULL DEFAULT 1,
    MODIFY COLUMN ability_import_status VARCHAR(32) NOT NULL DEFAULT 'PENDING';
