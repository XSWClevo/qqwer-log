# Boss Zhipin Auto Chat Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build a single-file Tampermonkey script that filters Boss 直聘 job cards, auto-opens chat for matching jobs, sends a user-entered greeting, and pauses safely on risk prompts or unsupported DOM states.

**Architecture:** Keep the implementation as one userscript file under `docs/`, but split logic internally into storage, panel, parser, filters, scheduler, chat actions, guards, and self-test helpers. Use pure utility functions for filter logic so they can be verified with a Node-based self-test entrypoint, and keep browser-only bootstrapping behind environment guards.

**Tech Stack:** JavaScript, Tampermonkey APIs, browser DOM APIs, Node.js for syntax/self-test verification.

---

## File Structure

Files to create:

- `docs/boss-zhipin-auto-chat.user.js`
  The single deployable userscript with metadata header, floating panel, filtering/scheduler/chat logic, and Node-compatible self-tests.

Files to modify:

- `docs/superpowers/plans/2026-06-11-boss-zhipin-auto-chat-implementation.md`
  Mark completed tasks if the plan needs to stay synchronized during execution.

## Task 1: Testable Utility Surface

**Files:**
- Create: `docs/boss-zhipin-auto-chat.user.js`

- [ ] **Step 1: Write the failing self-test entrypoint**

Add a Node-only `runSelfTests()` section near the end of `docs/boss-zhipin-auto-chat.user.js` that asserts these behaviors:

```js
assertEqual(parseCsvList(' Java, Go , ,Python '), ['java', 'go', 'python'], 'parseCsvList trims and lowercases');
assertEqual(parseSalaryRange('20-40K'), { min: 20, max: 40 }, 'parseSalaryRange parses range');
assertEqual(parseSalaryRange('15K'), { min: 15, max: 15 }, 'parseSalaryRange parses single value');
assertEqual(normalizeBossActivity('今日活跃'), 'today', 'normalizeBossActivity maps today label');
assertEqual(normalizeBossActivity('2日内活跃'), 'within3d', 'normalizeBossActivity maps recent-day label');
assertEqual(matchesBossActivity('within3d', 'today'), true, 'activity threshold includes today');
assertEqual(matchesBossActivity('just_active', 'within3d'), false, 'strict threshold rejects weaker activity');
```

- [ ] **Step 2: Run the self-test and verify it fails**

Run:

```bash
node docs/boss-zhipin-auto-chat.user.js --self-test
```

Expected: process exits non-zero because the utility functions do not exist yet.

- [ ] **Step 3: Implement utility functions and test harness**

Implement these pure helpers before any browser-only boot logic:

```js
function parseCsvList(input) { /* trim, lowercase, drop blanks */ }
function parseSalaryRange(text) { /* parse K values into {min,max} or null */ }
function normalizeBossActivity(text) { /* any|just_active|today|within3d|stale */ }
function matchesBossActivity(requiredLevel, actualLevel) { /* threshold compare */ }
function normalizeText(text) { /* string normalization */ }
```

Keep them free of DOM/Tampermonkey dependencies so they can run under Node.

- [ ] **Step 4: Run the self-test and verify it passes**

Run:

```bash
node docs/boss-zhipin-auto-chat.user.js --self-test
```

Expected: stdout ends with `Self-tests passed` and exit code is `0`.

## Task 2: Filtering, State, and Panel

**Files:**
- Modify: `docs/boss-zhipin-auto-chat.user.js`

- [ ] **Step 1: Write a failing filter behavior test**

Extend `runSelfTests()` with:

```js
const sampleJob = {
  jobTitle: 'Java 开发工程师',
  companyName: '测试科技',
  salaryText: '20-40K',
  bossActivityText: '今日活跃',
  summaryText: '微服务 Spring Cloud'
};

const config = {
  bossActivity: 'today',
  minSalary: 15,
  maxSalary: 45,
  jobKeywords: ['java'],
  excludeKeywords: ['外包'],
  excludeCompanies: ['黑名单公司']
};

assertEqual(evaluateJobFilters(sampleJob, config).passed, true, 'evaluateJobFilters allows matching card');
assertEqual(
  evaluateJobFilters({ ...sampleJob, companyName: '黑名单公司(上海)' }, config).reason,
  'company_excluded',
  'evaluateJobFilters blocks excluded company'
);
```

- [ ] **Step 2: Run the self-test and verify it fails**

Run:

```bash
node docs/boss-zhipin-auto-chat.user.js --self-test
```

Expected: non-zero exit because `evaluateJobFilters` is undefined or incomplete.

- [ ] **Step 3: Implement config, filter evaluation, and floating panel**

Add:

- a persisted default config object;
- `evaluateJobFilters(job, config)` returning `{ passed, reason, parsedSalary, normalizedActivity }`;
- Tampermonkey/localStorage-backed `loadConfig()` and `saveConfig()`;
- floating panel UI with greeting textarea, filter fields, rate-limit fields, status area, and Start/Pause/Resume/Stop actions.

Use a fixed-position panel and keep styles namespaced, for example with a `boss-auto-chat-` prefix.

- [ ] **Step 4: Run the self-test and syntax check**

Run:

```bash
node docs/boss-zhipin-auto-chat.user.js --self-test
node --check docs/boss-zhipin-auto-chat.user.js
```

Expected: self-tests pass and syntax check exits `0`.

## Task 3: DOM Parsing, Scheduler, and Auto Send Flow

**Files:**
- Modify: `docs/boss-zhipin-auto-chat.user.js`

- [ ] **Step 1: Write a failing queue/state test**

Extend `runSelfTests()` with:

```js
const runtime = createInitialRuntime();
assertEqual(runtime.status, 'idle', 'runtime starts idle');
assertEqual(runtime.counts.success, 0, 'runtime success count starts at zero');
assertEqual(buildProcessedKey({
  companyName: '测试科技',
  jobTitle: 'Java 开发工程师',
  bossName: '张三'
}), '测试科技::java 开发工程师::张三', 'processed key is stable');
```

- [ ] **Step 2: Run the self-test and verify it fails**

Run:

```bash
node docs/boss-zhipin-auto-chat.user.js --self-test
```

Expected: non-zero exit because runtime helpers do not exist yet.

- [ ] **Step 3: Implement browser runtime**

Add:

- `createInitialRuntime()` and runtime mutation helpers;
- DOM parser for visible job cards and action buttons;
- serialized scheduler with interval plus jitter;
- chat opener that clicks the matching button, waits for the message box, injects the greeting, and clicks send;
- risk prompt detection and safe pause behavior;
- duplicate protection using a per-run processed key set;
- concise console logging with `[boss-auto-chat]` prefix.

Guard browser boot with:

```js
if (typeof window !== 'undefined' && typeof document !== 'undefined') {
  bootstrap();
}
```

- [ ] **Step 4: Run full verification**

Run:

```bash
node docs/boss-zhipin-auto-chat.user.js --self-test
node --check docs/boss-zhipin-auto-chat.user.js
```

Expected: both commands exit `0`.

## Task 4: Usage Notes and Final Verification

**Files:**
- Modify: `docs/boss-zhipin-auto-chat.user.js`

- [ ] **Step 1: Add top-of-file usage notes**

Document:

- supported page intent;
- install/import steps for Tampermonkey;
- required manual login precondition;
- supported filters and rate-limit settings;
- pause behavior on CAPTCHA/risk prompts.

- [ ] **Step 2: Run final verification**

Run:

```bash
node docs/boss-zhipin-auto-chat.user.js --self-test
node --check docs/boss-zhipin-auto-chat.user.js
git diff -- docs/boss-zhipin-auto-chat.user.js docs/superpowers/plans/2026-06-11-boss-zhipin-auto-chat-implementation.md
```

Expected:

- self-tests pass;
- syntax check passes;
- diff only shows the userscript and plan changes you intended.

## Self-Review

Spec coverage check:

- floating panel, greeting textarea, filters, and rate limits are covered by Task 2;
- serialized execution, auto-send flow, duplicate protection, and pause rules are covered by Task 3;
- persistence and usage notes are covered by Tasks 2 and 4;
- Node-based validation for pure logic and syntax is covered by Tasks 1 through 4.

Placeholder scan:

- no `TODO`, `TBD`, or "implement later" placeholders are left in the plan.

Type consistency:

- helper names used across tasks are `parseCsvList`, `parseSalaryRange`, `normalizeBossActivity`, `matchesBossActivity`, `evaluateJobFilters`, `createInitialRuntime`, and `buildProcessedKey`.

## Execution Handoff

Plan complete and saved to `docs/superpowers/plans/2026-06-11-boss-zhipin-auto-chat-implementation.md`.

Default execution for this request is inline implementation in the current session using `superpowers:executing-plans`, because the user explicitly requested immediate implementation.
