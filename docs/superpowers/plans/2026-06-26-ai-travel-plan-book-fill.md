# AI游宜昌计划书填写 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 基于赛事模板生成一份可直接提交的《AI游宜昌》参赛计划书成品。

**Architecture:** 保留原始 DOCX 模板的整体版式和标题层级，替换提示语为正式正文内容，并输出为新的 DOCX 文件。完成后通过渲染生成页面 PNG，检查页面排版、换页和文字显示是否正常。

**Tech Stack:** Python `python-docx`、LibreOffice 渲染、PNG 视觉校验

---

### Task 1: 生成计划书正文并写回模板

**Files:**
- Modify: `/Users/xsw/Downloads/2026年湖北省首届“火山杯”AI创客大赛计划书（参考模板）.docx`
- Create: `/Users/xsw/Downloads/2026年湖北省首届“火山杯”AI创客大赛计划书-AI游宜昌-填写版.docx`

- [ ] **Step 1: 梳理模板段落位置**

读取模板正文段落，确认标题段、一级标题段与说明段的索引位置，确保内容写入位置稳定。

- [ ] **Step 2: 编写正式正文内容**

按以下八个部分生成正式内容：项目/产品/服务介绍、市场分析定位、商业模式、营销策略、财务分析、风险分析、团队介绍、附件。内容约束为青年AI创新赛道、1人团队、方案设计/原型阶段。

- [ ] **Step 3: 写出新的 DOCX 成品**

保留模板版式，替换标题为项目正式名称，并将各章节说明替换为正式正文，输出新文件：

```text
/Users/xsw/Downloads/2026年湖北省首届“火山杯”AI创客大赛计划书-AI游宜昌-填写版.docx
```

### Task 2: 渲染并校验文档版式

**Files:**
- Verify: `/Users/xsw/Downloads/2026年湖北省首届“火山杯”AI创客大赛计划书-AI游宜昌-填写版.docx`
- Create: `/tmp/ai-travel-plan-book-render/page-*.png`

- [ ] **Step 1: 运行文档渲染**

Run:

```bash
env TMPDIR=/private/tmp '/Users/xsw/.cache/codex-runtimes/codex-primary-runtime/dependencies/python/bin/python3' \
  '/Users/xsw/.codex/plugins/cache/openai-primary-runtime/documents/26.623.12021/skills/documents/render_docx.py' \
  '/Users/xsw/Downloads/2026年湖北省首届“火山杯”AI创客大赛计划书-AI游宜昌-填写版.docx' \
  --output_dir /tmp/ai-travel-plan-book-render
```

Expected: 生成 `page-1.png` 等页面图片，无渲染报错阻断。

- [ ] **Step 2: 检查页面观感**

逐页查看 PNG，重点检查标题、段落换行、跨页、重叠和字体显示是否正常。

- [ ] **Step 3: 如有必要迭代修正**

若页面存在明显问题，则回到 DOCX 内容或段落组织进行调整后重新渲染，直到成品可交付。
