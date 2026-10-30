#!/usr/bin/env python3
"""Regresja rozliczeń wydatków projektów Android/Desktop."""
from decimal import Decimal, ROUND_HALF_UP
from pathlib import Path

root = Path(".")
store = (root / "app/src/main/java/com/edwinkarolczyk/edhome/ProjectStore.java").read_text(encoding="utf-8")
main = (root / "app/src/main/java/com/edwinkarolczyk/edhome/MainActivity.java").read_text(encoding="utf-8")
desktop = (root / "desktop/src/main/java/com/edhome/desktop/EdhomeDesktop.java").read_text(encoding="utf-8")

# Obie platformy muszą liczyć ilość * cenę w groszach z HALF_UP, bez double.
assert "java.math.RoundingMode.HALF_UP" in store
assert "java.math.RoundingMode.HALF_UP" in main
assert "java.math.RoundingMode.HALF_UP" in desktop
assert "Math.round(c.getLong(0)*c.getLong(1)/1000.0)" not in store
assert "Math.round(qty*price/1000.0)" not in main
assert "desktopProjectCostValue(cost)" in desktop

# Android i Desktop obejmują projekt nadrzędny oraz wszystkie poziomy podprojektów.
assert "ProjectStore.descendantIds" in main.split("private void renderProjectCosts", 1)[1].split("private void showProjectPlanSuggestions", 1)[0]
assert "desktopProjectDescendsFrom(owner,projectId)" in desktop
assert "static Set<Long> descendantIds(" in store

# Tylko kupione/zapłacone są poniesione; planned pozostaje osobną kategorią.
for source in (store, main, desktop):
    assert '"bought".equals' in source and '"paid".equals' in source
assert 'else if("planned".equals(status))' in main
assert 'else if("planned".equals(status))' in desktop

# Podsumowanie ma być ostatnim blokiem szczegółów, nie zmianą sekcji postępu/czynności.
android_detail = main.split("private void projects()", 1)[1].split("private void bindProjectLaborSummary", 1)[0]
assert "Koszt planowany:" not in android_detail
assert android_detail.index("renderProjectTasks(project);") < android_detail.index("renderProjectResources(project.id);") < android_detail.index("renderProjectCosts(project.id);")
android_costs = main.split("private void renderProjectCosts", 1)[1].split("private void showProjectPlanSuggestions", 1)[0]
assert "LinearLayout expenseSummary=card();" in android_costs
assert "root.addView(expenseSummary);" not in android_costs
assert "body.addView(box, params);" in main.split("private LinearLayout card()", 1)[1].split("private int taskStatusSurface", 1)[0]
assert 'Podsumowanie wydatków' in android_costs
desktop_detail = desktop.split("private JComponent desktopProjectDetail", 1)[1].split("private static String desktopProjectCostStatus", 1)[0]
assert "list.add(desktopProjectCostPanel(projectId));" in desktop_detail
desktop_costs = desktop.split("private JPanel desktopProjectCostPanel", 1)[1].split("private void addDesktopProjectCost", 1)[0]
assert desktop_costs.rfind("section.add(expenseSummary);") > desktop_costs.rfind("if(count==0)")
assert "Podsumowanie wydatków" in desktop_costs

# Przykład zagnieżdżonego projektu: 10 planowane + 20 kupione + 30 zapłacone.
# Obcy projekt nie może wejść do sumy.
costs = [
    (10, 1000, 1000, "planned"),
    (11, 2000, 1000, "bought"),
    (12, 3000, 1000, "paid"),
    (99, 9000, 1000, "bought"),
]
descendants = {10, 11, 12}
total = spent = planned = 0
for project_id, qty_milli, unit_price_grosz, status in costs:
    if project_id not in descendants:
        continue
    amount = int((Decimal(qty_milli) * Decimal(unit_price_grosz) / Decimal(1000)).quantize(
        Decimal("1"), rounding=ROUND_HALF_UP))
    total += amount
    if status in ("bought", "paid"):
        spent += amount
    elif status == "planned":
        planned += amount
assert (total, spent, planned) == (6000, 5000, 1000)

# Połówki grosza zaokrąglamy w górę; wartości poniżej połowy w dół.
def amount(qty, price):
    return int((Decimal(qty) * Decimal(price) / Decimal(1000)).quantize(
        Decimal("1"), rounding=ROUND_HALF_UP))
assert amount(500, 1) == 1
assert amount(499, 1) == 0
assert amount(1500, 1) == 2
assert "Koszty projektu nie są księgowane w PayCheck." in main
print("Project expense summary and Android/Desktop accounting: PASS")
