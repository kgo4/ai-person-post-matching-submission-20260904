-- Repair only mojibake rows (UTF-8 bytes decoded as latin1).
-- Normal Chinese rows do not match these markers and remain untouched.
UPDATE learning_resource
SET ability_name = CONVERT(CAST(CONVERT(ability_name USING latin1) AS BINARY) USING utf8mb4),
    title = CONVERT(CAST(CONVERT(title USING latin1) AS BINARY) USING utf8mb4),
    description = CASE
        WHEN description IS NULL THEN NULL
        ELSE CONVERT(CAST(CONVERT(description USING latin1) AS BINARY) USING utf8mb4)
    END
WHERE ability_name REGEXP 'Ã|Â|å|æ|ç|è|é|ï|ð'
   OR title REGEXP 'Ã|Â|å|æ|ç|è|é|ï|ð'
   OR description REGEXP 'Ã|Â|å|æ|ç|è|é|ï|ð';
