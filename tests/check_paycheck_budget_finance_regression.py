"""Kontrakt integracyjny P0: matematyka jest wywoływana w produkcji."""
from pathlib import Path

root = Path(__file__).resolve().parents[1] / "app/src/main/java/com/edwinkarolczyk/edhome"
budget = (root / "PaycheckMonthlyBudget.java").read_text(encoding="utf-8")
main = (root / "MainActivity.java").read_text(encoding="utf-8")
finance = (root / "PaycheckBudgetSettlementMath.java").read_text(encoding="utf-8")
split = (root / "PaycheckBudgetSplitMath.java").read_text(encoding="utf-8")

arrears = budget.split("static List<Arrear> sharedArrearsBefore(", 1)[1].split(
    "static long sharedCarryBefore(", 1
)[0]
assert "creditAppliedTo(item,cursor)" in arrears
assert "PaycheckBudgetSettlementMath.outstanding(" in arrears

credits = budget.split("static List<Credit> sharedCreditsBefore(", 1)[1].split(
    "static long sharedCreditBefore(", 1
)[0]
assert "sharedSplitSurplus(db,item,cursor)" in credits
assert "creditAppliedFrom(item,cursor)" in credits

due = budget.split("static long remainingDue(", 1)[1].split(
    "static PaycheckBudgetSplitMath.Result allocateSplit(", 1
)[0]
assert "sharedMatchedActual(db,item,month)" in due
assert "creditAppliedTo(item,month)" in due
assert "PaycheckBudgetSettlementMath.outstanding(" in due

atomic = budget.split("static PaycheckBudgetSplitMath.Result allocateSplit(", 1)[1].split(
    "static long allocatedForOperation(", 1
)[0]
assert "PaycheckBudgetSplitMath.calculate(" in atomic
assert "saveWithEvents(prefs,all,events);" in atomic
assert "splitSurplusesGrosz.put(" in atomic
assert '"OVERPAYMENT"' in atomic and '"UNDERPAYMENT"' in atomic
assert "sharedTransaction(db,operationId)" in atomic and "tx.confirmed" in atomic

candidate = main.split("private PaycheckMonthlyBudget.Item suggestSharedBudgetItem(", 1)[1].split(
    "private java.util.List<PaycheckMonthlyBudget.Item> budgetSplitItems(", 1
)[0]
assert "PaycheckMonthlyBudget.remainingDue(" in candidate
split_candidates = main.split("private java.util.List<PaycheckMonthlyBudget.Item> budgetSplitItems(", 1)[1].split(
    "private boolean sameBudgetItemSet(", 1
)[0]
assert "PaycheckMonthlyBudget.remainingDue(" in split_candidates
assert "if (already > 0) continue;" not in split_candidates
assert "PaycheckMonthlyBudget.plannedAmount(item,month)" not in split_candidates
assert "PaycheckMonthlyBudget.allocateSplit(" in main
assert "Math.subtractExact" in finance
assert "Math.subtractExact(transactionGrosz, plannedTotal)" in split
print("Budżet P0: integracja rozliczeń, kredytów i podziału przelewów PASS")
