#!/usr/bin/env python3
"""EDHOME Desktop approved-scope regression contract."""
from pathlib import Path

root = Path(__file__).resolve().parents[1]
desktop = (root / "desktop/src/main/java/com/edhome/desktop/EdhomeDesktop.java").read_text(encoding="utf-8")
scanner = (root / "desktop/src/main/java/com/edhome/desktop/DesktopHardwareScanner.java").read_text(encoding="utf-8")
labels = (root / "desktop/src/main/java/com/edhome/desktop/DesktopQrLabels.java").read_text(encoding="utf-8")
bank = (root / "desktop/src/main/java/com/edhome/desktop/DesktopBankImporter.java").read_text(encoding="utf-8")
private = (root / "desktop/src/main/java/com/edhome/desktop/DesktopPrivatePaycheckVault.java").read_text(encoding="utf-8")
report = (root / "desktop/src/main/java/com/edhome/desktop/DesktopReportPdf.java").read_text(encoding="utf-8")
chart = (root / "desktop/src/main/java/com/edhome/desktop/DesktopPaycheckChart.java").read_text(encoding="utf-8")
gradle = (root / "desktop/build.gradle").read_text(encoding="utf-8")
readme = (root / "desktop/README.md").read_text(encoding="utf-8")

# QR printing + exact approved formats + custom labels.
for marker in (
    "40 × 30 mm", "50 × 30 mm", "70 × 50 mm", "A4 — zbiorczo",
    "Własny rozmiar", "showQrLabelWorkflow", "showBulkQrLabels",
    "QR / etykieta", "labelPrinter"
):
    assert marker in labels + desktop, f"Missing QR label contract: {marker}"
assert "org.apache.pdfbox:pdfbox" in gradle
assert "EDHOME:STORAGE:1:" in labels

# Scanner hardware paths and post-scan actions/defaults.
for marker in (
    "readCodeFromWebcam", "readNfcUid", "TerminalFactory",
    "Skanuj QR kamerą", "Skanuj NFC", "Ustaw domyślną akcję",
    "Wypożycz", "Zwrot", "scanDefaultKey"
):
    assert marker in scanner + desktop, f"Missing scanner contract: {marker}"

# Bank import must remain local evidence first, with dedupe and supported file types.
for marker in (
    "PDF", "CSV", "XLSX", "VeloBank", "mBank",
    "archiveOriginalPdf", "bankEvidenceExists", "bank_evidence_queue",
    "pending", "statement_key", "Historia importów"
):
    assert marker in bank + desktop, f"Missing PayCheck bank import contract: {marker}"

# Shared PayCheck desktop tools.
for marker in (
    "showPaycheckAnalysis", "showPaycheckGoals", "showPaycheckBulkEdit",
    "paycheck_goals", "paycheck_goal_allocations", "DesktopPaycheckChart"
):
    assert marker in desktop + chart, f"Missing PayCheck desktop tool: {marker}"

# Private PayCheck must stay encrypted and outside ordinary snapshot tables.
for marker in (
    "EDHOME_PRIVATE_PAYCHECK_ENCRYPTED_V1",
    "PBKDF2WithHmacSHA256", "AES/GCM/NoPadding",
    "DesktopPrivatePaycheckVault", "Prywatny PayCheck"
):
    assert marker in private + desktop, f"Missing private PayCheck contract: {marker}"
assert "private_paycheck" not in desktop.split("mutableTable(", 1)[-1]

# Calendar/tasks printing.
for marker in ("DesktopReportPdf", "printTableReport", "Kalendarz", "Czynności", "Zadania"):
    assert marker in report + desktop, f"Missing report printing contract: {marker}"

# Product direction remains explicit.
assert "Desktop = APK 1:1" in readme
assert "Zatwierdzony zakres Desktop" in readme

print("EDHOME Desktop approved scope: PASS")

# Quick-task wizard and real desktop calendar.
for marker in (
    "showQuickTaskWizard", "Ile to zajmie?", "Kiedy ma być zrobione?",
    "QuickTaskDraft", "parseQuickDuration", "parseQuickDueDate",
    "Wklej listę", "DesktopCalendarPanel", "addTaskOnCalendarDate",
    "openCalendarEvent", "inspection_until", "oc_until"
):
    assert marker in desktop, f"Missing quick-task/calendar contract: {marker}"
calendar = (root / "desktop/src/main/java/com/edhome/desktop/DesktopCalendarPanel.java").read_text(encoding="utf-8")
for marker in ("Pon","Niedz","Poprzedni","Następny","Agenda","Podwójne kliknięcie"):
    assert marker in calendar, f"Missing desktop calendar UI contract: {marker}"
print("EDHOME Desktop quick tasks + calendar: PASS")

# Desktop must be single-instance.
for marker in (
    "desktop-instance.lock", "tryLock()", "OverlappingFileLockException",
    "EDHOME Desktop jest już uruchomiony", "releaseSingleInstanceLock"
):
    assert marker in desktop, f"Missing single-instance contract: {marker}"
print("EDHOME Desktop single-instance guard: PASS")

# Annual waste schedule wizard.
for marker in (
    "showWasteYearWizard", "Roczny kreator odpadów",
    "Styczeń", "parseWasteMonthDays", "WasteScheduleEntry",
    "Wpisuję daty ODBIORU", "Wpisuję bezpośrednio daty WYSTAWIENIA",
    "Pomiń miesiąc", "← Wstecz"
):
    assert marker in desktop, f"Missing annual waste wizard contract: {marker}"
print("EDHOME Desktop annual waste wizard: PASS")
