-- =====================================================================
-- V118: 能力标签分层体系 —— 技能→能力规则映射表 + 能力层(L1)种子数据
-- 说明：解决「agent 提取技能词(Vue3/Java/MySQL)无法归属到能力层」的问题。
--   1) skill_taxonomy_map 规则表：技能词 -> 归属能力标签(高置信人工维护)
--   2) 能力层(L1)种子标签：tag_level=1 / parent_id=0，作为技能归类的锚点
-- =====================================================================

-- ---------------------------------------------------------------------
-- 1. 技能→能力规则映射表
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `skill_taxonomy_map` (
    `id`              BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `skill_name`      VARCHAR(128) NOT NULL COMMENT '技能词（如 Vue3、SpringBoot、MySQL）',
    `ability_tag_id`  BIGINT       NOT NULL COMMENT '归属的能力标签ID（L1）',
    `category`        VARCHAR(32)  NOT NULL DEFAULT 'TECHNICAL' COMMENT '分类：TECHNICAL/SOFT/BUSINESS',
    `confidence`      DECIMAL(5,2) NOT NULL DEFAULT 1.00 COMMENT '规则置信度：人工维护=1.00，AI建议<1.00',
    `source`          VARCHAR(20)  NOT NULL DEFAULT 'MANUAL' COMMENT '来源：MANUAL/AI_SUGGEST/VECTOR_AUTO',
    `status`          TINYINT      NOT NULL DEFAULT 1 COMMENT '状态：0停用，1启用',
    `created_time`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_time`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE INDEX `uk_skill_name` (`skill_name`),
    INDEX `idx_ability_tag_id` (`ability_tag_id`)
) ENGINE = InnoDB AUTO_INCREMENT = 1 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '技能→能力规则映射表' ROW_FORMAT = Dynamic;

-- ---------------------------------------------------------------------
-- 2. 能力层(L1)种子标签：固定 tag_code + 固定 ID，canonical_tag_id 指向自身
--    tag_level=1 / parent_id=0 / is_system=1 / status=1 / source_type=MANUAL
--    注意：embedding_vector 由应用侧 batchGenerateVectors() 生成
-- ---------------------------------------------------------------------
INSERT IGNORE INTO `ability_tag`
    (`id`, `tag_code`, `tag_name`, `parent_id`, `tag_category`, `domain`, `tag_level`, `description`, `sort_order`, `is_system`, `status`, `source_type`, `canonical_tag_id`, `created_by`)
VALUES
    (2001, 'SEED_COMP_FRONTEND', '前端开发', 0, 'TECHNICAL', 'GENERAL', 1, 'Web/小程序等前端界面开发能力，涵盖框架、交互、性能与工程化', 1, 1, 1, 'MANUAL', 2001, 0),
    (2002, 'SEED_COMP_BACKEND', '后端开发', 0, 'TECHNICAL', 'GENERAL', 1, '服务端业务逻辑、接口与微服务开发能力', 2, 1, 1, 'MANUAL', 2002, 0),
    (2003, 'SEED_COMP_DATABASE', '数据库设计与管理', 0, 'TECHNICAL', 'GENERAL', 1, '数据库建模、SQL、缓存与数据存储方案设计能力', 3, 1, 1, 'MANUAL', 2003, 0),
    (2004, 'SEED_COMP_DATA_ANALYSIS', '数据分析', 0, 'TECHNICAL', 'BIG_DATA', 1, '数据清洗、统计、可视化与洞察提炼能力', 4, 1, 1, 'MANUAL', 2004, 0),
    (2005, 'SEED_COMP_TESTING', '软件测试', 0, 'TECHNICAL', 'GENERAL', 1, '测试设计、自动化测试与质量保障能力', 5, 1, 1, 'MANUAL', 2005, 0),
    (2006, 'SEED_COMP_DEVOPS', 'DevOps与运维', 0, 'TECHNICAL', 'CLOUD', 1, '持续集成/部署、容器化、监控与系统运维能力', 6, 1, 1, 'MANUAL', 2006, 0),
    (2007, 'SEED_COMP_MOBILE', '移动开发', 0, 'TECHNICAL', 'GENERAL', 1, 'Android/iOS/跨端移动应用开发能力', 7, 1, 1, 'MANUAL', 2007, 0),
    (2008, 'SEED_COMP_ML', '算法与机器学习', 0, 'TECHNICAL', 'AI', 1, '机器学习、深度学习、NLP 等算法建模与应用能力', 8, 1, 1, 'MANUAL', 2008, 0),
    (2009, 'SEED_COMP_ARCHITECTURE', '系统架构设计', 0, 'TECHNICAL', 'GENERAL', 1, '系统架构、分布式设计与技术选型能力', 9, 1, 1, 'MANUAL', 2009, 0),
    (2010, 'SEED_COMP_SECURITY', '网络安全', 0, 'TECHNICAL', 'GENERAL', 1, '应用/网络安全防护、攻防与安全合规能力', 10, 1, 1, 'MANUAL', 2010, 0),
    (2011, 'SEED_COMP_CLOUD', '云计算', 0, 'TECHNICAL', 'CLOUD', 1, '云平台资源、云原生应用的规划与使用能力', 11, 1, 1, 'MANUAL', 2011, 0),
    (2012, 'SEED_COMP_BIGDATA', '大数据开发', 0, 'TECHNICAL', 'BIG_DATA', 1, '大数据平台、批流处理与数据仓库建设能力', 12, 1, 1, 'MANUAL', 2012, 0),
    (2101, 'SEED_COMP_COMMUNICATION', '沟通协调', 0, 'SOFT', 'GENERAL', 1, '跨角色沟通、冲突化解与协作推进能力', 21, 1, 1, 'MANUAL', 2101, 0),
    (2102, 'SEED_COMP_TEAMWORK', '团队协作', 0, 'SOFT', 'GENERAL', 1, '团队配合、知识共享与共同目标达成能力', 22, 1, 1, 'MANUAL', 2102, 0),
    (2103, 'SEED_COMP_PROJECT_MGMT', '项目管理', 0, 'SOFT', 'GENERAL', 1, '项目计划、进度、资源与风险管理能力', 23, 1, 1, 'MANUAL', 2103, 0),
    (2104, 'SEED_COMP_LEADERSHIP', '领导力', 0, 'SOFT', 'GENERAL', 1, '团队激励、决策与组织影响力', 24, 1, 1, 'MANUAL', 2104, 0),
    (2201, 'SEED_COMP_REQ_ANALYSIS', '需求分析', 0, 'BUSINESS', 'GENERAL', 1, '需求调研、澄清、建模与范围管理能力', 31, 1, 1, 'MANUAL', 2201, 0),
    (2202, 'SEED_COMP_PRODUCT_DESIGN', '产品设计', 0, 'BUSINESS', 'GENERAL', 1, '产品规划、原型设计与用户体验能力', 32, 1, 1, 'MANUAL', 2202, 0),
    (2203, 'SEED_COMP_BIZ_OPERATION', '业务运营', 0, 'BUSINESS', 'GENERAL', 1, '业务运营策略、数据分析驱动的增长能力', 33, 1, 1, 'MANUAL', 2203, 0);

-- ---------------------------------------------------------------------
-- 3. 技能→能力规则映射种子（覆盖简历高频技能词，可人工继续维护）
-- ---------------------------------------------------------------------
INSERT IGNORE INTO `skill_taxonomy_map` (`skill_name`, `ability_tag_id`, `category`, `confidence`, `source`, `status`) VALUES
    -- 前端开发
    ('Vue3', 2001, 'TECHNICAL', 1.00, 'MANUAL', 1),
    ('Vue', 2001, 'TECHNICAL', 1.00, 'MANUAL', 1),
    ('React', 2001, 'TECHNICAL', 1.00, 'MANUAL', 1),
    ('Angular', 2001, 'TECHNICAL', 1.00, 'MANUAL', 1),
    ('HTML', 2001, 'TECHNICAL', 1.00, 'MANUAL', 1),
    ('HTML5', 2001, 'TECHNICAL', 1.00, 'MANUAL', 1),
    ('CSS', 2001, 'TECHNICAL', 1.00, 'MANUAL', 1),
    ('CSS3', 2001, 'TECHNICAL', 1.00, 'MANUAL', 1),
    ('JavaScript', 2001, 'TECHNICAL', 1.00, 'MANUAL', 1),
    ('TypeScript', 2001, 'TECHNICAL', 1.00, 'MANUAL', 1),
    ('小程序', 2001, 'TECHNICAL', 1.00, 'MANUAL', 1),
    -- 后端开发
    ('Java', 2002, 'TECHNICAL', 1.00, 'MANUAL', 1),
    ('Spring Boot', 2002, 'TECHNICAL', 1.00, 'MANUAL', 1),
    ('SpringBoot', 2002, 'TECHNICAL', 1.00, 'MANUAL', 1),
    ('Spring Cloud', 2002, 'TECHNICAL', 1.00, 'MANUAL', 1),
    ('Go', 2002, 'TECHNICAL', 1.00, 'MANUAL', 1),
    ('Golang', 2002, 'TECHNICAL', 1.00, 'MANUAL', 1),
    ('Python', 2002, 'TECHNICAL', 1.00, 'MANUAL', 1),
    ('Node.js', 2002, 'TECHNICAL', 1.00, 'MANUAL', 1),
    ('微服务', 2002, 'TECHNICAL', 1.00, 'MANUAL', 1),
    ('MyBatis', 2002, 'TECHNICAL', 1.00, 'MANUAL', 1),
    -- 数据库设计与管理
    ('MySQL', 2003, 'TECHNICAL', 1.00, 'MANUAL', 1),
    ('Oracle', 2003, 'TECHNICAL', 1.00, 'MANUAL', 1),
    ('Redis', 2003, 'TECHNICAL', 1.00, 'MANUAL', 1),
    ('MongoDB', 2003, 'TECHNICAL', 1.00, 'MANUAL', 1),
    ('SQL Server', 2003, 'TECHNICAL', 1.00, 'MANUAL', 1),
    ('数据库设计', 2003, 'TECHNICAL', 1.00, 'MANUAL', 1),
    -- 数据分析
    ('数据分析', 2004, 'TECHNICAL', 1.00, 'MANUAL', 1),
    ('Pandas', 2004, 'TECHNICAL', 1.00, 'MANUAL', 1),
    ('NumPy', 2004, 'TECHNICAL', 1.00, 'MANUAL', 1),
    ('数据可视化', 2004, 'TECHNICAL', 1.00, 'MANUAL', 1),
    -- 软件测试
    ('自动化测试', 2005, 'TECHNICAL', 1.00, 'MANUAL', 1),
    ('Selenium', 2005, 'TECHNICAL', 1.00, 'MANUAL', 1),
    ('JUnit', 2005, 'TECHNICAL', 1.00, 'MANUAL', 1),
    ('测试用例', 2005, 'TECHNICAL', 1.00, 'MANUAL', 1),
    -- DevOps与运维
    ('Docker', 2006, 'TECHNICAL', 1.00, 'MANUAL', 1),
    ('Kubernetes', 2006, 'TECHNICAL', 1.00, 'MANUAL', 1),
    ('K8s', 2006, 'TECHNICAL', 1.00, 'MANUAL', 1),
    ('Jenkins', 2006, 'TECHNICAL', 1.00, 'MANUAL', 1),
    ('CI/CD', 2006, 'TECHNICAL', 1.00, 'MANUAL', 1),
    ('Linux', 2006, 'TECHNICAL', 1.00, 'MANUAL', 1),
    ('运维', 2006, 'TECHNICAL', 1.00, 'MANUAL', 1),
    -- 移动开发
    ('Android', 2007, 'TECHNICAL', 1.00, 'MANUAL', 1),
    ('iOS', 2007, 'TECHNICAL', 1.00, 'MANUAL', 1),
    ('Flutter', 2007, 'TECHNICAL', 1.00, 'MANUAL', 1),
    ('React Native', 2007, 'TECHNICAL', 1.00, 'MANUAL', 1),
    -- 算法与机器学习
    ('机器学习', 2008, 'TECHNICAL', 1.00, 'MANUAL', 1),
    ('深度学习', 2008, 'TECHNICAL', 1.00, 'MANUAL', 1),
    ('TensorFlow', 2008, 'TECHNICAL', 1.00, 'MANUAL', 1),
    ('PyTorch', 2008, 'TECHNICAL', 1.00, 'MANUAL', 1),
    ('NLP', 2008, 'TECHNICAL', 1.00, 'MANUAL', 1);
