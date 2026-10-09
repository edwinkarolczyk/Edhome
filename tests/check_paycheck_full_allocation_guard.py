#!/usr/bin/env python3
"""PayCheck P0: nie wolno przydzielać jednego przelewu drugi raz."""
from pathlib import Path
import subprocess
import tempfile

root=Path(__file__).resolve().parents[1]
src=root/"app/src/main/java/com/edwinkarolczyk/edhome"
budget=(src/"PaycheckMonthlyBudget.java").read_text(encoding="utf-8")
guard=(src/"PaycheckBudgetAllocationGuard.java").read_text(encoding="utf-8")
smoke=root/"tests/task_rules/PaycheckBudgetAllocationGuardSmoke.java"

assert "allocatedForOperation(items,operationId,tx.amountGrosz)" in budget
assert "static long allocatedForOperation(List<Item> items, String operationId," in budget
method=budget.split("static long allocatedForOperation(List<Item> items, String operationId,",1)[1].split(
    "static boolean unmatch(",1)[0]
assert "PaycheckBudgetAllocationGuard.addUsed(" in method
assert "item.matchedOperationIds.contains(operationId)" in method
assert "item.matchedAllocationsGrosz.get(operationId)" in method
assert "item.splitSurplusesGrosz.get(operationId)" in method
assert "allocatedGrosz==null?paymentGrosz:allocatedGrosz" in guard
assert "Math.addExact(alreadyUsed,Math.addExact(allocated,surplus))" in guard
assert "if(used>paymentGrosz)" in guard

with tempfile.TemporaryDirectory(prefix="edhome-paycheck-guard-") as tmp:
    subprocess.run(["javac","-encoding","UTF-8","-d",tmp,
        str(src/"PaycheckBudgetAllocationGuard.java"),str(smoke)],check=True)
    subprocess.run(["java","-cp",tmp,
        "com.edwinkarolczyk.edhome.PaycheckBudgetAllocationGuardSmoke"],check=True)

print("PASS: pełny przelew, alokacja częściowa i nadpłata nie są liczone podwójnie")
