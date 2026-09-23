#!/usr/bin/env python3
"""OC calendar pickers and optional one-year-minus-one-day suggestion."""
from pathlib import Path

root = Path("app/src/main/java/com/edwinkarolczyk/edhome")
main = (root / "MainActivity.java").read_text(encoding="utf-8")
dates = (root / "VehiclePolicyDates.java").read_text(encoding="utf-8")
policy = (root / "VehiclePolicyStore.java").read_text(encoding="utf-8")
section = main.split("private void editVehiclePolicy(", 1)[1].split(
    "private String vehicleDeadline(", 1)[0]
for token in (
    "from.setFocusable(false);",
    "until.setFocusable(false);",
    "from.setOnClickListener(v -> pickVehiclePolicyDate(from, () -> {",
    "until.setOnClickListener(v -> {",
    "automaticEnd.setChecked(false);",
    "pickVehiclePolicyDate(until, null);",
    "automaticEnd.setOnCheckedChangeListener((button, checked) -> {",
    "if (automaticEnd.isChecked())",
    "if (checked)",
    "VehiclePolicyDates.yearMinusDay(",
    "lightDialogForm(form);",
    "VehiclePolicyStore.add(",
):
    assert token in section, token
assert "private void pickVehiclePolicyDate(EditText input, Runnable afterPick)" in main
assert "new DatePickerDialog(this, (picker, year, month, day)" in main
assert "plusYears(1).minusDays(1)" in dates
assert "VehiclePolicyStore.dates" not in dates
assert 'VehicleRules.optionalDate' in policy and "LocalDate.parse(until).isBefore(" in policy
assert "PaycheckStore" not in section
print("OC pickers, optional renewal suggestion and manual override: PASS")
