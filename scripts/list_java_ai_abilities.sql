SET NAMES utf8mb4;
SELECT pam.id, pam.ability_name, HEX(pam.ability_name), pam.min_required_level
FROM post_ability_model pam JOIN post_post pp ON pp.id=pam.post_id
WHERE pp.post_name LIKE 'Java AI%'
ORDER BY pam.id;
