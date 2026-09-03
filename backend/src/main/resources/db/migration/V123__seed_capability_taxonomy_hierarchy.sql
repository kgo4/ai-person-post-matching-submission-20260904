-- Canonical L0 -> L1 -> L2 taxonomy for fresh databases.
-- V118 SEED_COMP_* rows retain their IDs and become assessable L2 nodes.
INSERT IGNORE INTO ability_tag
  (id, tag_code, tag_name, parent_id, tag_category, domain, tag_level, description, sort_order, is_system, status, source_type, canonical_tag_id, created_by)
VALUES
  (1901, 'ROOT_TECHNICAL', '技术能力', 0, 'TECHNICAL', 'GENERAL', 0, '技术能力分类根节点', 1, 1, 1, 'MANUAL', 1901, 0),
  (1902, 'ROOT_SOFT', '软技能', 0, 'SOFT', 'GENERAL', 0, '软技能分类根节点', 2, 1, 1, 'MANUAL', 1902, 0),
  (1903, 'ROOT_BUSINESS', '业务能力', 0, 'BUSINESS', 'GENERAL', 0, '业务能力分类根节点', 3, 1, 1, 'MANUAL', 1903, 0),
  (1911, 'DOMAIN_APPLICATION_DEV', '应用开发', 1901, 'TECHNICAL', 'GENERAL', 1, '应用开发能力域', 10, 1, 1, 'MANUAL', 1911, 0),
  (1912, 'DOMAIN_DATA_INTELLIGENCE', '数据与智能', 1901, 'TECHNICAL', 'BIG_DATA', 1, '数据与智能能力域', 20, 1, 1, 'MANUAL', 1912, 0),
  (1913, 'DOMAIN_ARCH_INFRA', '架构与基础设施', 1901, 'TECHNICAL', 'CLOUD', 1, '架构与基础设施能力域', 30, 1, 1, 'MANUAL', 1913, 0),
  (1914, 'DOMAIN_QUALITY_ENGINEERING', '质量工程', 1901, 'TECHNICAL', 'GENERAL', 1, '质量工程能力域', 40, 1, 1, 'MANUAL', 1914, 0),
  (1921, 'DOMAIN_COLLABORATION_MANAGEMENT', '协作与管理', 1902, 'SOFT', 'GENERAL', 1, '协作与管理能力域', 10, 1, 1, 'MANUAL', 1921, 0),
  (1931, 'DOMAIN_PRODUCT_BUSINESS', '产品与业务运营', 1903, 'BUSINESS', 'GENERAL', 1, '产品与业务运营能力域', 10, 1, 1, 'MANUAL', 1931, 0);

UPDATE ability_tag SET parent_id = 1911, tag_level = 2 WHERE id IN (2001, 2002, 2007);
UPDATE ability_tag SET parent_id = 1912, tag_level = 2 WHERE id IN (2003, 2004, 2008, 2012);
UPDATE ability_tag SET parent_id = 1913, tag_level = 2 WHERE id IN (2006, 2009, 2010, 2011);
UPDATE ability_tag SET parent_id = 1914, tag_level = 2 WHERE id = 2005;
UPDATE ability_tag SET parent_id = 1921, tag_level = 2 WHERE id IN (2101, 2102, 2103, 2104);
UPDATE ability_tag SET parent_id = 1931, tag_level = 2 WHERE id IN (2201, 2202, 2203);
