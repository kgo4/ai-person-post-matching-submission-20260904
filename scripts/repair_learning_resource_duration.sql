-- Normalize duration to ASCII-safe values so database/client charset cannot
-- turn Chinese duration text into mojibake.
UPDATE learning_resource
SET duration = CASE
    WHEN difficulty_level >= 5 THEN '12h'
    WHEN difficulty_level = 4 THEN '10h'
    WHEN difficulty_level = 3 THEN '8h'
    WHEN difficulty_level = 2 THEN '6h'
    ELSE '4h'
END
WHERE duration IS NULL
   OR HEX(duration) REGEXP 'E7|C3|C2|EFBC|E6'
   OR duration LIKE '%?%';
