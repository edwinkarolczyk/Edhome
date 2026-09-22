package com.edwinkarolczyk.edhome;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

/**
 * Shared savings goals are planning allocations, never bank transfers or expenses.
 * Private goal rows are deliberately impossible in the unauthenticated Beta.
 */
final class PaycheckGoalsStore {
    private PaycheckGoalsStore() { }

    static void create(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE paycheck_goals ("
            + "id INTEGER PRIMARY KEY AUTOINCREMENT, "
            + "scope TEXT NOT NULL CHECK(scope='shared'), "
            + "name TEXT NOT NULL, target_grosz INTEGER NOT NULL "
            + "CHECK(target_grosz BETWEEN 1 AND 99999999999), "
            + "created_at INTEGER NOT NULL)");
        db.execSQL("CREATE TABLE paycheck_goal_allocations ("
            + "id INTEGER PRIMARY KEY AUTOINCREMENT, "
            + "operation_id TEXT NOT NULL UNIQUE, "
            + "goal_id INTEGER NOT NULL, "
            + "amount_grosz INTEGER NOT NULL "
            + "CHECK(amount_grosz BETWEEN 1 AND 99999999999), "
            + "created_at INTEGER NOT NULL)");
        db.execSQL("CREATE INDEX paycheck_goal_allocations_goal_idx "
            + "ON paycheck_goal_allocations(goal_id,id)");
    }

    static long addGoal(SQLiteDatabase db, String name, long targetGrosz) {
        if (name == null || name.trim().isEmpty() || name.trim().length() > 80
                || targetGrosz < 1 || targetGrosz > MoneyRules.MAX_GROSZ)
            throw new IllegalArgumentException("Nazwa 1–80 znaków i dodatni cel w PLN.");
        ContentValues goal = new ContentValues();
        goal.put("scope", "shared");
        goal.put("name", name.trim());
        goal.put("target_grosz", targetGrosz);
        goal.put("created_at", System.currentTimeMillis());
        return db.insertOrThrow("paycheck_goals", null, goal);
    }

    static long allocated(SQLiteDatabase db, long goalId) {
        try (Cursor c = db.rawQuery("SELECT COALESCE(SUM(amount_grosz),0) "
                + "FROM paycheck_goal_allocations WHERE goal_id=?",
                new String[]{Long.toString(goalId)})) {
            return c.moveToFirst() ? c.getLong(0) : 0;
        }
    }

    static String allocate(SQLiteDatabase db, long goalId, String operationId,
            long grosz) {
        if (operationId == null || !operationId.matches("[0-9a-fA-F-]{36}")
                || grosz < 1 || grosz > MoneyRules.MAX_GROSZ)
            throw new IllegalArgumentException("Nieprawidłowa wpłata na cel.");
        db.beginTransaction();
        try {
            try (Cursor prior = db.rawQuery(
                    "SELECT 1 FROM paycheck_goal_allocations WHERE operation_id=?",
                    new String[]{operationId})) {
                if (prior.moveToFirst()) return "DUPLICATE";
            }
            long target;
            try (Cursor goal = db.rawQuery(
                    "SELECT target_grosz FROM paycheck_goals "
                    + "WHERE id=? AND scope='shared'",
                    new String[]{Long.toString(goalId)})) {
                if (!goal.moveToFirst()) return "MISSING_GOAL";
                target = goal.getLong(0);
            }
            long already = allocated(db, goalId);
            if (already > target || grosz > target - already) return "OVER_TARGET";
            ContentValues row = new ContentValues();
            row.put("operation_id", operationId);
            row.put("goal_id", goalId);
            row.put("amount_grosz", grosz);
            row.put("created_at", System.currentTimeMillis());
            db.insertOrThrow("paycheck_goal_allocations", null, row);
            db.setTransactionSuccessful();
            return "COMMITTED";
        } finally {
            db.endTransaction();
        }
    }
}
