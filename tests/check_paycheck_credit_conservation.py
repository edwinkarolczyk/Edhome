#!/usr/bin/env python3
"""P0: test wykonywalny kwot kredytu + bramka integracji synchronizacji."""
from pathlib import Path
import subprocess
import tempfile

root=Path(__file__).resolve().parents[1]
sources=root/"app/src/main/java/com/edwinkarolczyk/edhome"
smoke=root/"tests/task_rules/PaycheckBudgetCreditConservationSmoke.java"
budget=(sources/"PaycheckMonthlyBudget.java").read_text(encoding="utf-8")
patch=(sources/"PaycheckBudgetSyncPatch.java").read_text(encoding="utf-8")
math=(sources/"PaycheckBudgetCreditMath.java").read_text(encoding="utf-8")

assert "static void assertCreditConservation(SQLiteDatabase db,String serialized)" in budget
assert 'sharedMatchedActual(db,item,month)' in budget
assert 'sharedSplitSurplus(db,item,month)' in budget
assert 'PaycheckBudgetCreditMath.requireSourceAvailable(' in budget
assert 'PaycheckBudgetCreditMath.requireTargetWithinPlan(' in budget
assert 'PaycheckMonthlyBudget.assertCreditConservation(db,updatedItems)' in patch
assert patch.index('PaycheckMonthlyBudget.assertCreditConservation(db,updatedItems)') < patch.index(
    'PaycheckBudgetSqliteStore.reconcileRaw(db,')
assert '"budget_credits"' in patch and 'new SyncRecordStore.SyncConflict(' in patch
assert "Math.addExact" in math

with tempfile.TemporaryDirectory(prefix="edhome-credits-") as folder:
    subprocess.run([
        "javac","-encoding","UTF-8","-d",folder,
        str(sources/"PaycheckBudgetCreditMath.java"),str(smoke)
    ],check=True)
    subprocess.run([
        "java","-cp",folder,
        "com.edwinkarolczyk.edhome.PaycheckBudgetCreditConservationSmoke"
    ],check=True)

print("PASS: kredyt źródła, limit miesiąca docelowego i walidacja przed mutacją SQL")
