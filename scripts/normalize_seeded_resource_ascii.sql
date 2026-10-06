-- Avoid client-encoding dependent literals: use the authoritative UTF-8 ability name.
UPDATE learning_resource
SET title = ability_name,
    description = ability_name
WHERE LEFT(resource_code, 4) = 'POST';
