#!/usr/bin/env python3
"""Regression contract: hourly project-work reminders must never auto-pause a session."""
from pathlib import Path

source = Path(
    "app/src/main/java/com/edwinkarolczyk/edhome/ProjectWorkNotification.java"
).read_text(encoding="utf-8")
check = source.split(
    "static void handleCheckAction(Context context, long taskId)", 1
)[1].split("private static PendingIntent stopNoticeAction", 1)[0]
timeout = source.split(
    "static void handleTimeout(Context context, long taskId, long started)", 1
)[1].split("static boolean isActive(", 1)[0]

# The check only reads the active session and reschedules a reminder.
assert "SELECT started_at FROM project_task_work_sessions" in check
assert "ended_at IS NULL" in check
assert "scheduleNextCheck(context, taskId)" in check
assert "manager.notify(reminderNotificationId(taskId), notification)" in check
assert "ended_at" not in check.replace("ended_at IS NULL", "")
assert "worked_minutes" not in check
assert "ProjectStore.pauseWorkAt" not in timeout
assert "handleCheckAction(context, taskId)" in timeout
assert "scheduleTimeout(context, taskId, activeStarted)" not in source
assert "manager.cancel(reminderNotificationId(taskId))" in source
print("Project timer: reminder leaves active session running and schedules next reminder: PASS")
