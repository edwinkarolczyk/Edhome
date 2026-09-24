#!/usr/bin/env python3
"""Per-vehicle view collapse is durable, independent, and read-only for domain data."""
from pathlib import Path
main=Path("app/src/main/java/com/edwinkarolczyk/edhome/MainActivity.java").read_text(encoding="utf-8")
needle='final String collapseKey = "vehicle_collapsed_" + id;'
assert needle in main
assert 'final boolean collapsed = prefs.getBoolean(collapseKey, false);' in main
assert 'prefs.edit().putBoolean(collapseKey, !collapsed).apply();' in main
assert 'if (collapsed) continue;' in main
assert 'collapsed ? "▸ Rozwiń pojazd" : "▾ Zwiń pojazd"' in main
vehicle=main.index('private void vehicles()')
heading=main.index(needle,vehicle)
skip=main.index('if (collapsed) continue;',heading)
details=main.index('box.addView(text("Przebieg: "',skip)
assert vehicle<heading<skip<details
assert 'prefs.edit().putBoolean(collapseKey, !collapsed).apply();\n                        render();' in main
print("Vehicle collapse: independent ID keys, persisted on restart, no data writes PASS")
