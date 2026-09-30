#!/usr/bin/env python3
"""EDHOME Desktop approved-scope regression contract."""
from pathlib import Path
import re

root = Path(__file__).resolve().parents[1]
desktop = (root / "desktop/src/main/java/com/edhome/desktop/EdhomeDesktop.java").read_text(encoding="utf-8")
scanner = (root / "desktop/src/main/java/com/edhome/desktop/DesktopHardwareScanner.java").read_text(encoding="utf-8")
labels = (root / "desktop/src/main/java/com/edhome/desktop/DesktopQrLabels.java").read_text(encoding="utf-8")
bank = (root / "desktop/src/main/java/com/edhome/desktop/DesktopBankImporter.java").read_text(encoding="utf-8")
private = (root / "desktop/src/main/java/com/edhome/desktop/DesktopPrivatePaycheckVault.java").read_text(encoding="utf-8")
report = (root / "desktop/src/main/java/com/edhome/desktop/DesktopReportPdf.java").read_text(encoding="utf-8")
chart = (root / "desktop/src/main/java/com/edhome/desktop/DesktopPaycheckChart.java").read_text(encoding="utf-8")
budget = (root / "desktop/src/main/java/com/edhome/desktop/DesktopBudgetPlanner.java").read_text(encoding="utf-8")
budget_doc = (root / "desktop/src/main/java/com/edhome/desktop/DesktopBudgetDocumentReader.java").read_text(encoding="utf-8")
merchant_rules = (root / "desktop/src/main/java/com/edhome/desktop/DesktopMerchantRules.java").read_text(encoding="utf-8")
gradle = (root / "desktop/build.gradle").read_text(encoding="utf-8")
readme = (root / "desktop/README.md").read_text(encoding="utf-8")
workflow = (root / ".github/workflows/desktop-beta.yml").read_text(encoding="utf-8")

# One Desktop version everywhere: runtime, Gradle metadata and Windows package.
runtime_version = re.search(r'DESKTOP_VERSION = "([^"]+)"', desktop).group(1)
gradle_version = re.search(r"version = '([^']+)'", gradle).group(1)
workflow_version = re.search(r'DESKTOP_VERSION: "([^"]+)"', workflow).group(1)
assert runtime_version == gradle_version == workflow_version, (
    runtime_version, gradle_version, workflow_version
)
assert f"## Aktualny stan Desktop — {runtime_version}" in readme
print("EDHOME Desktop version metadata: PASS")

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
    "Wypożycz", "Zwrot", "scanDefaultKey",
    "showDesktopNfcManager", "readAndAssignDesktopNfc",
    "Zmień tag NFC", "Usuń powiązanie NFC", "commitDesktopNfcLink",
    "removeDesktopNfcTarget"
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

# Future budget planner: plan is separate from real ledger and supports document-guided setup.
for marker in (
    "Budżet przyszły", "Importuj PDF / XLSX / CSV", "Rachunek cykliczny",
    "Rata / kredyt", "Dochód cykliczny", "Co 2 miesiące",
    "Kiedy ten cykl się kończy?", "Prognoza 12 miesięcy",
    "paycheck-budget-plan.json",
    "Najpierw sprawdź dane", "Dodaj do budżetu", "Pomiń resztę pliku",
    "Zakończ import", "tryReadBankStatement", "Opis transakcji"
):
    assert marker in budget + desktop, f"Missing future budget contract: {marker}"
for marker in ("PDF", "XLSX", "CSV", "TAURON", "Wodociągi", "detectProvider", "extractAmounts"):
    assert marker in budget_doc, f"Missing budget document reader: {marker}"
assert 'mutableTable("paycheck_transactions")' not in budget
for marker in ("Sklep / odbiorca","nativeOpenFiles","FileDialog","subscriptions","fuel","loans"):
    assert marker in budget, f"Missing improved PayCheck import UX: {marker}"
for marker in ("paycheck-merchant-rules.json","BIEDRONKA","NETFLIX","TAURON","remember"):
    assert marker in merchant_rules, f"Missing merchant rules: {marker}"
for marker in ("Usuń wpisy","showPaycheckDelete","deletePaycheckTransaction",
               "deletePaycheckTransactions","Zaznacz wszystko",
               "MULTIPLE_INTERVAL_SELECTION","paycheck_operation_id"):
    assert marker in desktop, f"Missing Desktop PayCheck bulk delete: {marker}"
for marker in ('"pantry".equals(tableName)', '"vehicles".equals(tableName)',
               '"device_timers".equals(tableName)', 'hasOpenAuditSession()',
               'removeRowsByLong("vehicle_events"'):
    assert marker in desktop, f"Missing global main-record delete contract: {marker}"
for marker in ("Usuń cel","paycheck_goal_allocations","Usuń zaznaczone"):
    assert marker in desktop, f"Missing PayCheck related-record delete: {marker}"
for marker in ("paycheckDashboardStats", "paycheckMetricCard",
               "paycheckTransactionsView", "editPaycheckCategory",
               "paycheckMerchantStatsPanel", "paycheckCategoryStatsPanel",
               "Ostatnie transakcje", 'tabs.addTab("Sklepy"',
               'tabs.addTab("Kategorie"'):
    assert marker in desktop, f"Missing PayCheck dashboard UX: {marker}"
print("EDHOME Desktop future budget wizard: PASS")

# Private PayCheck must stay encrypted and outside ordinary snapshot tables.
for marker in (
    "EDHOME_PRIVATE_PAYCHECK_ENCRYPTED_V1",
    "PBKDF2WithHmacSHA256", "AES/GCM/NoPadding",
    "DesktopPrivatePaycheckVault", "Prywatny PayCheck"
):
    assert marker in private + desktop, f"Missing private PayCheck contract: {marker}"
assert "private_paycheck" not in desktop.split("mutableTable(", 1)[-1]
for marker in ('static int delete(Session session', '"subscriptions"', '"fuel"',
               '"insurance"', '"loans"'):
    assert marker in private, f"Missing private PayCheck delete/category compatibility: {marker}"

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

# Compact list cards and synchronized storage thumbnails.
for marker in (
    "card.setMaximumSize(new Dimension(Integer.MAX_VALUE, 94))",
    "compactActionButton", "storageThumbnailLabel",
    "storageThumbnails", "jpegBase64", "ImageIO.read"
):
    assert marker in desktop, f"Missing compact-card/thumbnail contract: {marker}"
print("EDHOME Desktop compact cards + thumbnails: PASS")

# Rooms/location browser: one physical tree over places + storage.
rooms = (root / "desktop/src/main/java/com/edhome/desktop/DesktopRoomsPanel.java").read_text(encoding="utf-8")
for marker in (
    '"Pomieszczenia"', 'DesktopRoomsPanel', 'editRoomNode',
    'table("places")', 'table("storage_items")'
):
    assert marker in desktop, f"Missing rooms integration contract: {marker}"
for marker in (
    'Bez przypisanego miejsca', 'Gdzie jest:', 'Pełna lokalizacja:',
    'parent_box_id', 'parent_id', 'place_id', 'storageThumbnails',
    'jpegBase64', 'Wybierz pomieszczenie, pudełko lub rzecz'
):
    assert marker in rooms, f"Missing rooms tree contract: {marker}"
print("EDHOME Desktop rooms/location tree: PASS")

# Visual building map and local route planner.
floor_map = (root / "desktop/src/main/java/com/edhome/desktop/DesktopFloorMapPanel.java").read_text(encoding="utf-8")
for marker in ('"Mapa"', 'DesktopFloorMapPanel', 'table("places")', 'table("storage_items")'):
    assert marker in desktop, f"Missing floor map integration: {marker}"
for marker in (
    '"Piwnica"', '"Parter"', '"Piętro"', '"Podwórko"', 'Jestem tutaj:', 'Gdzie jest:',
    'Dodaj przejście', 'Edytuj plan', 'shortestPath',
    'edhome-desktop-floor-map', 'floor-map.json',
    'parent_box_id', 'place_id', 'Pełna lokalizacja',
    'Import JPG', 'Import DXF', 'Rozmiar posesji',
    'Dodaj budynek', 'Dodaj pomieszczenie', 'Dodaj strefę',
    'Siatka: WŁ.', 'Snap: WŁ.', 'Właściwości',
    'floor-map-assets', 'propertyWidthMeters', 'propertyHeightMeters'
):
    assert marker in floor_map, f"Missing visual floor-map contract: {marker}"
print("EDHOME Desktop visual floor map + routing: PASS")

for marker in ('"pantry".equals(tableName)', '"vehicles".equals(tableName)', '"places".equals(tableName)', '"storage_items".equals(tableName)'):
    assert marker in desktop, f"Missing NFC-capable Desktop table: {marker}"


# Main Desktop window opens taller and stays within the usable Windows desktop.
for marker in (
    'getMaximumWindowBounds()',
    'Math.min(940, usableScreen.height - 16)',
    'setSize(initialWidth, initialHeight)'
):
    assert marker in desktop, f"Missing adaptive taller-window contract: {marker}"
assert 'setSize(1280, 800)' not in desktop
print("EDHOME Desktop adaptive taller window: PASS")
