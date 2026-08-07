"""Freeze the exact zero-progress scheduled tasks superseded by an auth retry."""
from __future__ import annotations

SAFE_DOMAINS = frozenset({"SALES", "FINANCE_TRANSACTION"})


def freeze_task_scope(snapshot: dict, recovery: int, owner: int,
                      item: dict, scope: dict) -> tuple[list[dict], list[int], list[int]]:
    tasks = sorted(snapshot.get("taskItems", []), key=lambda task: task["sourceTaskId"])
    if not tasks or snapshot.get("sourceTaskItemCount") != len(tasks):
        raise ValueError("task scope must contain every exact source-task item")
    ids = [task["sourceTaskId"] for task in tasks]
    if len(ids) != len(set(ids)):
        raise ValueError("task scope contains duplicate task ids")
    allowed_recoveries = {recovery, scope["scopedRecoveryId"]}
    for task in tasks:
        expected_task_recovery = (
            recovery if task["recoveryId"] == recovery else scope["sourceRecoveryId"]
        )
        safe = (
            task["recoveryId"] in allowed_recoveries
            and task["ownerUserId"] == owner
            and task["taskOwnerUserId"] == owner
            and task["sourceTaskId"] > 0
            and task["sourceTaskId"] == task["taskId"]
            and task["storeCode"] == task["taskStoreCode"]
            and task["siteCode"] == task["taskSiteCode"]
            and task["sourceDomain"] == task["dataDomain"]
            and task["sourceDomain"] in SAFE_DOMAINS
            and task["sourceCheckpoint"] == "PERSISTED_TASK_STATE"
            and task["resumePolicy"] == "AUTO_RESUME"
            and task["itemStatus"] == "PENDING"
            and task["status"] == "BLOCKED_AUTH"
            and task["authRecoveryId"] == expected_task_recovery
            and task["triggerMode"] == "SCHEDULED_DAILY"
            and task["pullType"] == "REPORT"
            and task["retryAction"] == "WAIT_FOR_AUTH"
            and task["checkpointCursor"] is None
            and task["nextResumePosition"] is None
            and task["lastSafeResponseSummary"] is None
            and task["processedItemCount"] == 0
            and task["requestCount"] == 0
            and task["finishedAt"] is None
            and task["isDeleted"] == 0
            and task["planEnabled"] == 1
            and task["planPaused"] == 0
        )
        if not safe:
            raise ValueError("task scope contains a non-safe scheduled task")
    root = [task for task in tasks if task["itemId"] == item["id"]]
    if (len(root) != 1 or root[0]["recoveryId"] != recovery
            or root[0]["projectCode"] != item["projectCode"]
            or root[0]["sourceTaskId"] != item["sourceTaskId"]):
        raise ValueError("manual-hold root task is not frozen exactly once")
    plan_ids = sorted({task["planId"] for task in tasks})
    return tasks, ids, plan_ids


__all__ = ["freeze_task_scope"]
