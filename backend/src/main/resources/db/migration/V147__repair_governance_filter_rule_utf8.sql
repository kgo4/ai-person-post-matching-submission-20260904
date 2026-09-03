-- 修复早期命令行导入时未声明连接字符集造成的系统内置规则乱码。
SET NAMES utf8mb4;

DELETE FROM governance_filter_rule WHERE source = 'SYSTEM';

INSERT INTO governance_filter_rule
    (scope, rule_type, rule_name, pattern_value, weight, source, review_status, description)
VALUES
    ('POST_JD', 'KEYWORD', '公司介绍', '公司介绍', 15, 'SYSTEM', 'APPROVED', '招聘广告噪声'),
    ('POST_JD', 'KEYWORD', '福利待遇', '福利待遇', 15, 'SYSTEM', 'APPROVED', '招聘广告噪声'),
    ('POST_JD', 'KEYWORD', '宣传话术', '宣传话术', 15, 'SYSTEM', 'APPROVED', '招聘广告噪声'),
    ('POST_JD', 'KEYWORD', '企业文化', '企业文化', 15, 'SYSTEM', 'APPROVED', '招聘广告噪声'),
    ('POST_JD', 'REGEX', '裸手机号', '[0-9]{11}', 15, 'SYSTEM', 'APPROVED', '联系方式噪声'),
    ('POST_JD', 'REGEX', '裸邮箱', '[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}', 15, 'SYSTEM', 'APPROVED', '联系方式噪声'),
    ('POST_JD', 'REGEX', '招聘联系方式', '(联系电话|联系方式|手机号|电话|手机|tel|phone)\\s*[:：]?\\s*[0-9]{3,4}[- ]?[0-9]{7,8}', 15, 'SYSTEM', 'APPROVED', '联系方式噪声'),
    ('POST_JD', 'REGEX', '招聘账号', '(qq|微信|vx|wx|微信号)\\s*[:：]?\\s*[A-Za-z0-9_-]{5,15}', 15, 'SYSTEM', 'APPROVED', '联系方式噪声'),
    ('POST_JD', 'REGEX', '公司广告段落', '(公司简介|企业介绍|关于我们|公司官网|公司地址)\\s*[:：][^\\n]{0,200}', 15, 'SYSTEM', 'APPROVED', '招聘广告噪声'),
    ('POST_JD', 'REGEX', '投递广告段落', '(简历投递|投递方式|应聘方式|招聘流程|面试流程)\\s*[:：][^\\n]{0,200}', 15, 'SYSTEM', 'APPROVED', '招聘广告噪声'),
    ('POST_JD', 'LENGTH', '正文过短', '100', 30, 'SYSTEM', 'APPROVED', '正文少于100字'),
    ('POST_JD', 'SECTION_MISSING', '缺少岗位职责描述', '岗位职责|工作内容|职责描述', 20, 'SYSTEM', 'APPROVED', '缺少实际职责内容'),
    ('PERSON_ABILITY', 'EXACT', '无意义能力词', '能力', 0, 'SYSTEM', 'APPROVED', '不能独立构成能力的泛化词'),
    ('PERSON_ABILITY', 'EXACT', '无意义能力词', '技能', 0, 'SYSTEM', 'APPROVED', '不能独立构成能力的泛化词'),
    ('PERSON_ABILITY', 'KEYWORD', '产品名称', 'chatgpt', 0, 'SYSTEM', 'APPROVED', '纯产品名不作为独立能力'),
    ('PERSON_ABILITY', 'KEYWORD', '产品名称', 'copilot', 0, 'SYSTEM', 'APPROVED', '纯产品名不作为独立能力');
