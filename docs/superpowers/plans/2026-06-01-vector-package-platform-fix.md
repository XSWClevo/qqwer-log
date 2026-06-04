# Vector Package Platform Fix Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 让安装包管理支持 Linux/macOS 多平台上传与分发，并修复 `/download`、`/latest`、升级链路在多平台共存时的错误查包问题。

**Architecture:** 以现有 `VectorPackage` 存储模型为基础，在服务层统一做 `osType` 和 `arch` 归一化，把“平台键”稳定为 `packageType + osType + arch`。前端只负责开放正确的上传选项和传递显式平台参数，后端负责归一化、默认值兜底和多平台最新包查询。

**Tech Stack:** Spring Boot, MyBatis-Plus, Vue 3, Element Plus, shell install script, JUnit 5

---

### Task 1: Add backend failing tests for platform normalization and package lookup

**Files:**
- Create: `log-analysis-backend/log-analysis-app/src/test/java/cn/mw/loganalysis/vector/service/VectorPackageServicePlatformTest.java`
- Modify: `log-analysis-backend/log-analysis-app/src/main/java/cn/mw/loganalysis/vector/service/VectorPackageService.java`

- [ ] **Step 1: Write the failing test**

Add tests that assert:
- `x86_64` normalizes to `amd64`
- `aarch64` normalizes to `arm64`
- `macos` normalizes to `darwin`
- latest package lookup for `linux/x86_64` and `darwin/arm64` resolve independently

- [ ] **Step 2: Run test to verify it fails**

Run: `cd /Users/xsw/custom_idea_project/qqwer/log-analysis-backend/log-analysis-app && mvn -Dtest=VectorPackageServicePlatformTest test`
Expected: FAIL because normalization helpers do not exist yet or lookup behavior does not match expectations.

- [ ] **Step 3: Write minimal implementation**

Implement normalization helpers inside `VectorPackageService` and route `uploadPackage` / `getLatestPackage` through them.

- [ ] **Step 4: Run test to verify it passes**

Run: `cd /Users/xsw/custom_idea_project/qqwer/log-analysis-backend/log-analysis-app && mvn -Dtest=VectorPackageServicePlatformTest test`
Expected: PASS

### Task 2: Fix backend controllers and install script defaults

**Files:**
- Modify: `log-analysis-backend/log-analysis-app/src/main/java/cn/mw/loganalysis/vector/controller/VectorPackageController.java`
- Modify: `log-analysis-backend/log-analysis-app/src/main/java/cn/mw/loganalysis/vector/controller/VectorAgentController.java`
- Modify: `log-analysis-backend/log-analysis-app/src/main/resources/scripts/install-agent.sh`

- [ ] **Step 1: Write or extend failing tests for controller-facing platform defaults**

Cover:
- `/api/vector/agents/download` default arch should be `amd64`
- package upgrade lookup should not hardcode `arm64`
- install script should send `amd64` for `x86_64`

- [ ] **Step 2: Run targeted verification to confirm failure or mismatch**

Run:
- `rg -n "defaultValue = \"arm64\"|arch = \"arm64\"|x86_64" /Users/xsw/custom_idea_project/qqwer/log-analysis-backend/log-analysis-app/src/main/java /Users/xsw/custom_idea_project/qqwer/log-analysis-backend/log-analysis-app/src/main/resources/scripts/install-agent.sh`

Expected: existing hardcoded values are present.

- [ ] **Step 3: Apply minimal implementation**

Update controller defaults and install script mapping so all public download and upgrade paths use normalized `amd64` / `arm64` values.

- [ ] **Step 4: Re-run backend tests and quick grep validation**

Run:
- `cd /Users/xsw/custom_idea_project/qqwer/log-analysis-backend/log-analysis-app && mvn -Dtest=VectorPackageServicePlatformTest test`
- `rg -n "defaultValue = \"arm64\"|arch = \"arm64\"|x86_64" /Users/xsw/custom_idea_project/qqwer/log-analysis-backend/log-analysis-app/src/main/java /Users/xsw/custom_idea_project/qqwer/log-analysis-backend/log-analysis-app/src/main/resources/scripts/install-agent.sh`

Expected:
- test PASS
- only intentional `x86_64` normalization branches remain

### Task 3: Fix frontend package manager platform options

**Files:**
- Modify: `log-analysis-frontend/src/views/vector/PackageManager.vue`

- [ ] **Step 1: Add the failing expectation**

Document the expected behavior in code by updating the component logic target:
- Linux must be selectable
- amd64 must be selectable
- platform labels must render clearly for both macOS and Linux

- [ ] **Step 2: Verify current UI code fails the expectation**

Run: `sed -n '1,220p' /Users/xsw/custom_idea_project/qqwer/log-analysis-frontend/src/views/vector/PackageManager.vue`
Expected: Linux and amd64 options are disabled.

- [ ] **Step 3: Write minimal implementation**

Open the disabled options, normalize label rendering, and keep the existing quick upload flow unchanged.

- [ ] **Step 4: Run lightweight verification**

Run:
- `cd /Users/xsw/custom_idea_project/qqwer/log-analysis-frontend && npm run build`

Expected: build succeeds.

### Task 4: Fix frontend upgrade lookup defaults

**Files:**
- Modify: `log-analysis-frontend/src/views/vector/MachineList.vue`

- [ ] **Step 1: Add the failing expectation**

Upgrade lookup should not default to `darwin/arm64` when machine platform is missing.

- [ ] **Step 2: Verify current code path is wrong**

Run: `sed -n '860,900p' /Users/xsw/custom_idea_project/qqwer/log-analysis-frontend/src/views/vector/MachineList.vue`
Expected: current request defaults to `darwin` and `arm64`.

- [ ] **Step 3: Write minimal implementation**

Change the request defaults to align with backend normalization and existing Linux-first machine defaults.

- [ ] **Step 4: Run lightweight verification**

Run: `cd /Users/xsw/custom_idea_project/qqwer/log-analysis-frontend && npm run build`
Expected: build succeeds.

### Task 5: End-to-end verification

**Files:**
- No code changes required

- [ ] **Step 1: Run backend test**

Run: `cd /Users/xsw/custom_idea_project/qqwer/log-analysis-backend/log-analysis-app && mvn -Dtest=VectorPackageServicePlatformTest test`
Expected: PASS

- [ ] **Step 2: Run frontend build**

Run: `cd /Users/xsw/custom_idea_project/qqwer/log-analysis-frontend && npm run build`
Expected: PASS

- [ ] **Step 3: Re-run vector-agent script regression**

Run: `bash /Users/xsw/custom_idea_project/qqwer/vector-agent/scripts/test-build-bundle.sh`
Expected: PASS

- [ ] **Step 4: Summarize residual risk**

Note that machine CPU arch is still not fully modeled end-to-end, so upgrade path currently relies on safer defaults plus normalization rather than true host architecture reporting.
