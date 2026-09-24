#!/usr/bin/env python3
"""EDHOME 0.6.0-beta.3: white native dialog contrast without dark-skin regressions."""
from pathlib import Path

root = Path("app/src/main/java/com/edwinkarolczyk/edhome")
main = (root / "MainActivity.java").read_text(encoding="utf-8")
contrast = (root / "DialogContrast.java").read_text(encoding="utf-8")
gradle = Path("app/build.gradle").read_text(encoding="utf-8")
workflow = Path(".github/workflows/android-beta.yml").read_text(encoding="utf-8")

def contrast_ratio(hex_value):
    parts = [int(hex_value[i:i + 2], 16) / 255 for i in (0, 2, 4)]
    rgb = [v / 12.92 if v <= .04045 else ((v + .055) / 1.055) ** 2.4
           for v in parts]
    lightness = sum(x * y for x, y in zip(rgb, (.2126, .7152, .0722)))
    return 1.05 / (lightness + .05)

for color in ("202124", "667085", "475467"):
    assert contrast_ratio(color) >= 4.5, color

for marker in (
    "static final int TEXT = 0xFF202124;",
    "static final int HINT = 0xFF667085;",
    "static final int LABEL = 0xFF475467;",
    "input.setTextColor(TEXT);",
    "input.setHintTextColor(HINT);",
    "input.setBackgroundTintList(",
    "android.R.attr.state_focused",
    "static ArrayAdapter<String> spinnerAdapter(",
    "@Override public View getView(",
    "@Override public View getDropDownView(",
    "((TextView) view).setTextColor(TEXT);",
    "view.setBackgroundColor(BACKGROUND);",
    "if (root instanceof Spinner) return;",
):
    assert marker in contrast, marker

assert "return DialogContrast.spinnerAdapter(this, labels);" in main
assert "DialogContrast.apply(form, lightDialogAccent());" in main
assert "return new ArrayAdapter<String>(this, android.R.layout.simple_spinner_item, items)" in main
assert "((TextView) view).setTextColor(ink);" in main  # dark screen stays unchanged
assert "UiSkin.TRAINER.equals(skin.name)" in main

def area(begin, end):
    return main.split(begin, 1)[1].split(end, 1)[0]

checks = (
    ("private void editVehicleTyres(", "private void changeVehicleTyres("),
    ("private void changeVehicleTyres(", "private String vehicleDeadline("),
    ("private void editVehicle(", "private void editVehicleEvent("),
    ("private void editVehicleEvent(", "private static final class PlaceEntry"),
    ("private void storageEditor(", "private void askStorageLend("),
    ("private void pantryProductDialog(", "private void audit()"),
    ("private void shoppingBoughtDialog(", "private void "),
)
for begin, end in checks:
    assert "lightDialogForm(" in area(begin, end), begin

assert "destination.setAdapter(lightDialogSpinnerAdapter(labels));" in area(
    "private void storageEditor(", "private void askStorageLend(")
assert "kind.setAdapter(lightDialogSpinnerAdapter(" in area(
    "private void editVehicleEvent(", "private static final class PlaceEntry")
assert "season.setAdapter(lightDialogSpinnerAdapter(" in area(
    "private void editVehicleTyres(", "private void changeVehicleTyres(")
assert "lightDialogSpinnerAdapter(" in area(
    "private Spinner pantryCategorySpinner(", "private void showPantryPriceHistory(")
assert "lightDialogForm(form);" in area(
    "private void showNewPantryProductDialog(String barcode, String operationId,\n"
    "            PantryProductLookup.Product found, String sourceDetails) {",
    "private void showPantryPriceHistory(")
assert "versionCode 79" in gradle
assert "versionNameSuffix ''" in gradle
assert "tests/check_dialog_contrast_contract.py" in workflow
print("Light dialog text/hint, spinner selection/dropdown and six-skin isolation: PASS")
