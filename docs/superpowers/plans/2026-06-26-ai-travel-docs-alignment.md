# AI游宜昌申报材料统一口径 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 统一申报书与计划书中的项目名称、团队口径、项目阶段和落地表述，形成一套自洽的参赛材料。

**Architecture:** 保留用户真实个人信息和申报书中的项目所在地不变，对计划书中的落地措辞做收敛，同时将两份材料中的项目标准名称和参赛身份统一。修改完成后分别渲染检查，确保版式稳定。

**Tech Stack:** Python `python-docx`、LibreOffice 渲染、PNG 视觉校验

---

### Task 1: 统一申报书关键字段

**Files:**
- Modify: `/Users/xsw/Downloads/2026年湖北省首届“火山杯”AI创客大赛申报书（参考模板）.docx`
- Create: `/Users/xsw/Downloads/2026年湖北省首届“火山杯”AI创客大赛申报书-AI游宜昌-统一版.docx`

- [ ] **Step 1: 保留真实信息并统一项目名称**

保留姓名、地址、电话、所在地、是否注册公司、项目阶段等真实信息，只将参赛项目名称统一为：

```text
AI游宜昌——面向新文旅场景的城市智能导览与内容生成平台
```

- [ ] **Step 2: 输出申报书统一版**

另存为统一版文件，不覆盖原始申报书模板。

### Task 2: 统一计划书落地表述

**Files:**
- Modify: `/Users/xsw/Downloads/2026年湖北省首届“火山杯”AI创客大赛计划书-AI游宜昌-填写版.docx`

- [ ] **Step 1: 收敛宜昌落地表述**

将容易被理解为“已在宜昌正式落地”的措辞，修改为“以宜昌为重点示范场景”“围绕宜昌重点场景开展验证”等更稳妥的表述。

- [ ] **Step 2: 保持项目阶段和团队身份一致**

保留“个人参赛、1人团队、创新/原型阶段”的当前口径，不引入新的组织或商业化表述。

### Task 3: 渲染检查

**Files:**
- Verify: `/Users/xsw/Downloads/2026年湖北省首届“火山杯”AI创客大赛申报书-AI游宜昌-统一版.docx`
- Verify: `/Users/xsw/Downloads/2026年湖北省首届“火山杯”AI创客大赛计划书-AI游宜昌-填写版.docx`

- [ ] **Step 1: 渲染申报书**

使用 `render_docx.py` 生成 PNG 页面，检查项目名称字段和整体布局。

- [ ] **Step 2: 渲染计划书**

使用 `render_docx.py` 生成 PNG 页面，检查修改后的段落换页和整体版式。

- [ ] **Step 3: 必要时进行小幅迭代**

若发现文本挤压、分页异常或字段显示问题，则进行小幅修正后再次渲染。
