# Noon And Competitor Stale Task Recovery Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make production scheduled tasks recover from JVM restarts, stale queued tasks, and stale running tasks without losing existing Noon or competitor-analysis capabilities.

**Architecture:** Align this branch to a production-capability superset by combining backend `master` Noon fixes with the competitor-analysis production hotfix branch. Then harden the durable task lifecycle: each scheduler tick recovers stale tasks, creates due tasks, claims executable queued work, and leaves unsupported work to stale recovery instead of silently occupying active locks.

**Tech Stack:** Spring Boot, Java 17, MyBatis mapper interfaces, focused JUnit tests, existing `noon_pull_task`, `operational_task`, and `operations_competitor_search_run` tables.

---

### Task 1: Baseline Alignment

**Files:**
- Merge source: `origin/hotfix/competitor-analysis-rank-depth-backend-20260615`
- Verify: `src/main/java/com/nuono/next/noonpull/NoonPullScheduledExecutionService.java`
- Verify: `src/main/java/com/nuono/next/competitoranalysis/CompetitorAnalysisMonitoringScheduler.java`

- [x] Merge competitor-analysis production hotfix branch into this branch.
- [x] Preserve current `origin/master` Noon order export behavior and competitor-analysis production files.
- [x] Verify both marker files exist.

### Task 2: Noon Queued Task Claiming

**Files:**
- Modify: `src/main/java/com/nuono/next/noonpull/NoonPullFoundationService.java`
- Modify: `src/main/java/com/nuono/next/noonpull/NoonPullScheduledExecutionService.java`
- Modify: `src/test/java/com/nuono/next/noonpull/NoonPullScheduledExecutionServiceTest.java`

- [x] Add a failing test where an existing `QUEUED` SALES REPORT task is present before `runOnce()` and is executed even though no new task was created in the current scheduler tick.
- [x] Implement a queued-task claim list that includes executable `QUEUED` tasks from repository state, deduplicated with newly created tasks.
- [x] Restrict the scheduled worker to SALES page-query, SALES report, and ORDER report tasks.

### Task 3: Noon Stale Queued Recovery

**Files:**
- Modify: `src/main/java/com/nuono/next/noonpull/NoonPullFoundationService.java`
- Modify: `src/main/java/com/nuono/next/noonpull/NoonPullScheduler.java`
- Modify: `src/test/java/com/nuono/next/noonpull/NoonPullSchedulerTest.java`

- [x] Add a failing test where an old `QUEUED` task is failed with a timeout-style failure and releases its active lock.
- [x] Add a scheduler tick test proving stale queued recovery runs before due-plan creation.
- [x] Implement stale queued recovery with configurable age and conservative default.

### Task 4: Competitor Stale Operational Task Recovery

**Files:**
- Modify: `src/main/java/com/nuono/next/system/task/OperationalTaskService.java`
- Modify: `src/main/java/com/nuono/next/competitoranalysis/CompetitorAnalysisRefreshService.java`
- Modify: `src/main/java/com/nuono/next/competitoranalysis/CompetitorAnalysisMonitoringScheduler.java`
- Modify: `src/test/java/com/nuono/next/competitoranalysis/CompetitorAnalysisRefreshServiceTest.java`
- Modify: `src/test/java/com/nuono/next/competitoranalysis/CompetitorAnalysisMonitoringSchedulerTest.java`

- [x] Add a failing test where a stale competitor refresh operational task and its linked search run are both marked failed.
- [x] Add a scheduler test proving stale recovery runs before rank/detail scheduled submission.
- [x] Implement task-type scoped stale recovery and ensure linked `operations_competitor_search_run` rows are failed with `FAILED_STALE`.

### Task 5: Verification And Documentation

**Files:**
- Modify: workspace `docs/decision-log.md`
- Modify: workspace `docs/current-focus.md`

- [x] Run focused Noon tests.
- [x] Run focused competitor tests.
- [x] Run lightweight compile.
- [x] Update shared context for durable decisions.
