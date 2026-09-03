-- Configurable weights are stored as percentage points (0-100).
-- Convert legacy fractional source weights exactly once; values already above 1 are untouched.
UPDATE source_weight_config
SET weight = ROUND(weight * 100, 6)
WHERE weight IS NOT NULL AND weight >= 0 AND weight <= 1;
