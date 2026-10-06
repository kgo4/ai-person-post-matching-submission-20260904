-- Persist the user's confirmation choices before asynchronous post import.
-- The payload is kept in MySQL because an Outbox TEXT payload is too small for large batches.
ALTER TABLE post_import_batch
    ADD COLUMN confirm_payload LONGTEXT NULL COMMENT '异步确认导入请求载荷';
