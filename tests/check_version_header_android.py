#!/usr/bin/env python3
"""Android: wyświetl numer zainstalowanej wersji na wszystkich nagłówkach EDHOME."""
from pathlib import Path

root=Path(__file__).resolve().parents[1]
src=(root/"app/src/main/java/com/edwinkarolczyk/edhome/MainActivity.java").read_text(
    encoding="utf-8")
gradle=(root/"app/build.gradle").read_text(encoding="utf-8")

header=src.split("private String appHeaderLabel() {",1)[1].split("\n    }",1)[0]
assert "BetaUpdater.isBeta()" in header, "Kanał Beta/Stable z rzeczywistego APK"
assert "BuildConfig.VERSION_NAME" in header, "Wersja musi się aktualizować automatycznie"
assert 'return "EDHOME  •  "' in header

shared=src.split("private void appTitleWithConnection() {",1)[1].split(
    "private void showShowcaseJump()",1)[0]
assert "TextView appTitle = text(appHeaderLabel(), 18, true);" in shared
assert "BuildConfig.VERSION_NAME, 10, false" in shared, (
    "Alternatywny widok musi także pokazywać numer wersji")
assert "row.addView(appTitle, new LinearLayout.LayoutParams(0, -2, 1f));" in shared
for method,marker in (
    ("private void setupPin() {", "private void unlockPin() {"),
    ("private void unlockPin() {", "private void appTitleWithConnection() {"),
    ("private void updates() {", "private void showUpdateDownloadQrCodes() {"),
):
    start=src.split(method,1)[1]
    section=start.split(marker,1)[0] if marker in start else start[:600]
    assert "appHeaderLabel()" in section, method
assert "version.addView(text(BuildConfig.VERSION_NAME" in src, (
    "Wersja i kompilacja w ekranie aktualizacji muszą pozostać dostępne")
# Weryfikuj bieżące wersjonowanie, nie konkretny numer starego wydania.
# Dzięki temu test dalej działa po kolejnych aktualizacjach EDHOME.
import re
codes=re.findall(r"(?m)^\\s*versionCode\\s+(\\d+)\\s*$",gradle)
names=re.findall(r"(?m)^\\s*versionName\\s+'(\\d+\\.\\d+\\.\\d+)'\\s*$",gradle)
assert len(codes)==1 and int(codes[0])>0,"Brak poprawnego versionCode"
assert len(names)==1,"Brak poprawnego numeru wersji Android"

print("PASS: EDHOME Beta / Stable + dynamiczna wersja na Start, modułach, PIN i Aktualizacjach")
