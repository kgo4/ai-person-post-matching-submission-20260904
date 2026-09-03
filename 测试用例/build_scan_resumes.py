# -*- coding: utf-8 -*-
"""
扫描件简历验证集生成脚本（可复现）
====================================
生成 5 份图片型 PDF 简历（模拟真实扫描件：无文本层、200dpi、轻噪点、轻微倾斜），
用于验证 ResumeFileParser 的"PDF 文本层 <50 字符自动触发百度 OCR 回退"链路。

生成产物：
  测试数据/简历样例/scan/scan-resume-001.pdf ~ 005.pdf   （扫描件样本）
  tests/resume-scan-test-cases.json                 （期望技能黄金集）

所有样本为授权合成简历（不含真实个人信息），与 7.1 数据合规声明一致。
运行：python tests/build_scan_resumes.py
"""
import json
import os
import random

from PIL import Image, ImageDraw, ImageFont, ImageFilter

BASE = os.path.dirname(os.path.abspath(__file__))
SUBMISSION_ROOT = os.path.normpath(os.path.join(BASE, '..'))
OUT_DIR = os.path.join(SUBMISSION_ROOT, '测试数据', '简历样例', 'scan')
FONT_PATH = r'C:\Windows\Fonts\simsun.ttc'
FONT_BOLD = r'C:\Windows\Fonts\msyh.ttc'

random.seed(20260903)

RESUMES = [
    {
        'caseId': 'SCAN-001',
        'name': '吴博文',
        'basic': '男 | 28岁 | 杭州 | 138****5678',
        'target': '大数据开发工程师',
        'summary': '专注批流一体大数据平台建设，熟悉从数据接入到数仓建模的完整链路。',
        'skills': [
            'Spark（Spark SQL / Structured Streaming，具备大规模作业调优经验）',
            'Flink（实时计算，曾支撑日均 20 亿条事件处理）',
            'Hive / HDFS 数仓建模与分区优化',
            'Kafka 高吞吐消息队列',
            'Scala 与 Python 数据开发',
        ],
        'projects': [
            '实时数仓平台：基于 Flink + Kafka 构建实时指标体系，延迟从小时级降至秒级',
            '离线调度治理：重构 Spark 作业血缘与资源隔离，集群利用率提升 30%',
        ],
        'education': '教育背景：数据科学与大数据技术，本科',
        'expectedSkills': ['Spark', 'Flink', 'Hive', 'Kafka', 'Scala'],
    },
    {
        'caseId': 'SCAN-002',
        'name': '郑晓彤',
        'basic': '女 | 26岁 | 北京 | 139****2211',
        'target': '计算机视觉算法工程师',
        'summary': '从事计算机视觉算法研发，专注目标检测与模型轻量化部署。',
        'skills': [
            'Python（主力语言，熟练使用 NumPy / OpenCV）',
            'TensorFlow 与 PyTorch 双框架训练经验',
            '目标检测（YOLO 系列 / Faster R-CNN）',
            '模型剪枝与量化，端侧部署落地',
            'CUDA 编程基础与 GPU 训练加速',
        ],
        'projects': [
            '缺陷检测系统：训练轻量化检测模型，产线检出率达 98.6%',
            '模型压缩：INT8 量化使推理耗时降低 60%，精度损失小于 1%',
        ],
        'education': '教育背景：计算机科学与技术，硕士',
        'expectedSkills': ['Python', 'TensorFlow', '目标检测', '模型量化', 'CUDA'],
    },
    {
        'caseId': 'SCAN-003',
        'name': '刘天泽',
        'basic': '男 | 31岁 | 深圳 | 136****8890',
        'target': '物联网嵌入式工程师',
        'summary': '深耕物联网终端固件开发，具备从传感器驱动到云接入的完整交付能力。',
        'skills': [
            '嵌入式 C 开发（STM32 / ESP32 平台）',
            'FreeRTOS 实时操作系统任务调度',
            'MQTT 与 CoAP 物联网通信协议',
            '低功耗设计与电池供电设备优化',
            '传感器数据采集与边缘滤波',
        ],
        'projects': [
            '智能表计固件：实现 MQTT 直连与 OTA 升级，批量部署 5 万台设备',
            '低功耗网关：休眠唤醒机制优化，待机电流降低至 12uA',
        ],
        'education': '教育背景：电子信息工程，本科',
        'expectedSkills': ['嵌入式C', 'FreeRTOS', 'MQTT', 'STM32', '低功耗'],
    },
    {
        'caseId': 'SCAN-004',
        'name': '孙嘉怡',
        'basic': '女 | 29岁 | 上海 | 137****3456',
        'target': '智能系统后端开发工程师',
        'summary': '负责智能客服与推荐系统后端服务，擅长高并发微服务架构。',
        'skills': [
            'Java（主力语言，熟悉 JVM 调优）',
            'Spring Cloud 微服务体系（注册中心 / 网关 / 配置中心）',
            'MySQL 索引优化与分库分表',
            'Redis 缓存设计与热点治理',
            '微服务治理与链路追踪',
        ],
        'projects': [
            '智能客服平台：支撑日均 300 万次会话消息，接口 P99 延迟 120ms',
            '服务治理改造：引入全链路压测与灰度发布，全年可用性 99.96%',
        ],
        'education': '教育背景：软件工程，本科',
        'expectedSkills': ['Java', 'Spring Cloud', 'MySQL', 'Redis', '微服务'],
    },
    {
        'caseId': 'SCAN-005',
        'name': '周子墨',
        'basic': '男 | 27岁 | 成都 | 135****7742',
        'target': 'AI 应用开发工程师',
        'summary': '专注大模型应用工程化，负责检索增强与智能问答系统落地。',
        'skills': [
            'Python（FastAPI 服务开发）',
            'LangChain 应用编排与 Agent 工作流',
            'RAG 检索增强（文档分块 / 向量检索 / 重排序）',
            'Prompt 工程与结构化输出校验',
            'Docker 容器化部署',
        ],
        'projects': [
            '企业知识问答：基于 RAG 架构，答案引用可溯源，采纳率 85%',
            '文档解析服务：多格式解析加分块索引，日处理文档 2 万份',
        ],
        'education': '教育背景：人工智能，本科',
        'expectedSkills': ['Python', 'LangChain', 'RAG', 'Prompt工程', 'Docker'],
    },
]

PAGE_W, PAGE_H = 1654, 2339  # A4 @ 200dpi


def wrap_text(draw, text, font, max_width):
    lines, buf = [], ''
    for ch in text:
        if draw.textlength(buf + ch, font=font) <= max_width:
            buf += ch
        else:
            lines.append(buf)
            buf = ch
    if buf:
        lines.append(buf)
    return lines


def render_resume_page(r):
    img = Image.new('L', (PAGE_W, PAGE_H), 250)
    draw = ImageDraw.Draw(img)
    f_name = ImageFont.truetype(FONT_BOLD, 64)
    f_sub = ImageFont.truetype(FONT_BOLD, 40)
    f_body = ImageFont.truetype(FONT_PATH, 36)
    f_small = ImageFont.truetype(FONT_PATH, 32)

    x, y = 140, 130
    draw.text((PAGE_W // 2 - draw.textlength(r['name'], font=f_name) // 2, y),
              r['name'], font=f_name, fill=25)
    y += 100
    draw.text((PAGE_W // 2 - draw.textlength(r['target'], font=f_sub) // 2, y),
              r['target'], font=f_sub, fill=40)
    y += 70
    draw.text((PAGE_W // 2 - draw.textlength(r['basic'], font=f_small) // 2, y),
              r['basic'], font=f_small, fill=60)
    y += 90

    def section(title, lines):
        nonlocal y
        draw.text((x, y), title, font=f_sub, fill=20)
        y += 62
        for line in lines:
            for sub in wrap_text(draw, '· ' + line, f_body, PAGE_W - 2 * x):
                draw.text((x + 20, y), sub, font=f_body, fill=35)
                y += 52
        y += 40

    section('职业概述', [r['summary']])
    section('核心技能', r['skills'])
    section('项目经历', r['projects'])
    draw.text((x, y), r['education'], font=f_body, fill=35)

    # ---- 扫描件仿真：轻微旋转 + 噪点 + 轻微模糊 ----
    angle = random.uniform(-0.6, 0.6)
    img = img.rotate(angle, resample=Image.BILINEAR, fillcolor=250)
    noise = Image.effect_noise((PAGE_W, PAGE_H), 14).point(lambda v: 128 + v // 2)
    img = Image.blend(img, noise, 0.06)
    img = img.filter(ImageFilter.GaussianBlur(0.6))
    return img.point(lambda v: min(255, int(v * 1.04)))


def main():
    os.makedirs(OUT_DIR, exist_ok=True)
    cases = []
    for r in RESUMES:
        img = render_resume_page(r)
        path = os.path.join(OUT_DIR, 'scan-resume-%s.pdf' % r['caseId'][-3:])
        img.save(path, 'PDF', resolution=200.0, quality=82)
        cases.append({
            'caseId': r['caseId'],
            'format': 'SCAN_PDF',
            'file': '测试数据/简历样例/scan/scan-resume-%s.pdf' % r['caseId'][-3:],
            'expectedSkills': r['expectedSkills'],
            'threshold': 0.9,
            'note': '图片型扫描件（无文本层），验证百度 OCR 回退链路；授权合成样本',
        })
        print('generated:', path)

    out = os.path.join(BASE, 'resume-scan-test-cases.json')
    with open(out, 'w', encoding='utf-8') as f:
        json.dump(cases, f, ensure_ascii=False, indent=2)
    print('test cases:', out, '(%d cases)' % len(cases))


if __name__ == '__main__':
    main()
