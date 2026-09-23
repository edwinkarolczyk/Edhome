#!/usr/bin/env python3
"""All vehicle dates are calendar-backed; optional deadlines can be cleared."""
from pathlib import Path
main=Path("app/src/main/java/com/edwinkarolczyk/edhome/MainActivity.java").read_text(encoding="utf-8")
gradle=Path("app/build.gradle").read_text(encoding="utf-8")
script=Path(".github/scripts/publish_public_channel.py").read_text(encoding="utf-8")
assert "private EditText vehicleCalendarDate(" in main
helper=main.split("private EditText vehicleCalendarDate(",1)[1].split(
    "private void editVehicle(",1)[0]
for marker in ("input.setFocusable(false);","input.setClickable(true);",
               "pickVehiclePolicyDate(input, null)",'input.setText("")',
               "if (optional)","form.addView(clear);"):
    assert marker in helper,marker
for start,stop,labels in (
    ("private void editVehicle(VehicleStore.Vehicle vehicle) {",
     "private void editVehicleEvent(",("OC do (opcjonalnie)", "Przegląd do (opcjonalnie)")),
    ("private void changeVehicleTyres(","private void pickVehiclePolicyDate(",
     ("Data zmiany kół",)),
    ("private void editVehicleEvent(","private static final class PlaceEntry",
     ("Data serwisu",)),
):
    chunk=main.split(start,1)[1].split(stop,1)[0]
    assert "vehicleCalendarDate(form" in chunk,start
    for label in labels: assert label in chunk,label
assert "versionCode 66" in gradle
assert "versionName '0.6.0.1'" in gradle
assert "versionNameSuffix ''" in gradle
assert 'suffix_match = re.search(' in script
print("Vehicle OC, inspection, service and tyre dates use calendar; optional clear: PASS")
