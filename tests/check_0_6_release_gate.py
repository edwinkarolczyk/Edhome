#!/usr/bin/env python3
"""Code-side gate for closing EDHOME 0.6.0.

This deliberately does NOT claim physical-device acceptance.  It prevents a
release candidate from losing an already implemented 0.6 safety/UX contract.
"""
from pathlib import Path
import re

root=Path(".")
src=root/"app/src/main/java/com/edwinkarolczyk/edhome"
main=(src/"MainActivity.java").read_text(encoding="utf-8")
backup=(src/"DataBackup.java").read_text(encoding="utf-8")
pay=(src/"PaycheckStore.java").read_text(encoding="utf-8")
listener=(src/"BankNotificationListener.java").read_text(encoding="utf-8")
hints=(src/"BankNotificationHints.java").read_text(encoding="utf-8")
receipt=(src/"BankReceiptNotifier.java").read_text(encoding="utf-8")
workflow=(root/".github/workflows/android-beta.yml").read_text(encoding="utf-8")
gradle=(root/"app/build.gradle").read_text(encoding="utf-8")
beta_manifest=(root/"app/src/beta/AndroidManifest.xml").read_text(encoding="utf-8")
stable_manifest=(root/"app/src/main/AndroidManifest.xml").read_text(encoding="utf-8")

# Release identity and data compatibility.
version=re.search(r"versionName '([^']+)'",gradle).group(1)
assert version.startswith("0.6.0."), version
assert "super(context, \"edhome-beta-preview.db\", null, 34)" in main
assert "private static final int DB_VERSION = 35;" in backup

# Shared PayCheck stays pending until a one-time explicit confirmation/match.
for token in (
    'values.put("status","pending")',
    "WHERE scope='shared' AND status='confirmed'",
    'static String matchStatement(',
    'CREATE UNIQUE INDEX paycheck_statement_key_unique',
):
    assert token in pay, token

# Bank listener: selected apps only, real connection state, reconnect and on-device test.
for token in (
    'BankNotificationHints.selected(this)',
    'static boolean isConnected()',
    'onListenerConnected()',
):
    assert token in listener, token
assert 'requestRebind(new ComponentName(this,' in main
for token in (
    'bank_listener_test_until',
    'TEST_WINDOW_MS=2L*60*1000',
    'armListenerTest(',
    'finishListenerTest(context,false)',
    'finishListenerTest(context,true)',
):
    assert token in hints, token
for token in (
    'Testuj nasłuch banku przez 2 minuty',
    'Nasłuch Androida:',
    'Ostatni nierozpoznany komunikat bankowy:',
    'Diagnostyka importu:',
):
    assert token in main, token
assert 'BankNotificationListener' in beta_manifest
assert 'BankNotificationListener' not in stable_manifest

# Import supports generic CSV, mBank CSV/XLSX and text-based Velo PDF, locally.
for token in (
    'BankStatementMbank.recognizes(text)',
    'BankStatementWorkbook.textRows(bytes)',
    'BankPdfText.extract(this,bytes)',
    'BankStatementVeloPdf.parse(pdfText)',
    'Intent.EXTRA_ALLOW_MULTIPLE',
    'BankEvidenceStore.ingest(',
    'showBankEvidenceQueue(0)',
    'BankEvidenceStore.match(',
    'Banki i potwierdzenia • kolejka',
    'countRecentStatementMatches(signal)',
):
    assert token in main, token
queue=(src/"BankEvidenceStore.java").read_text(encoding="utf-8")
for token in ('CREATE TABLE bank_evidence_queue',
              'evidence_key TEXT NOT NULL UNIQUE',
              "CHECK(state IN ('open','matched','dismissed'))",
              'db.beginTransaction();',
              'static String match('):
    assert token in queue,token
assert '{"bank_evidence_queue", "id", "evidence_key"' in backup
for token in (
    'diagStage="READ"',
    'diagStage="PDF_TEXT"',
    'diagStage="XLSX_TEXT"',
    'diagStage="MBANK_PARSE"',
    'diagStage="CSV_PARSE"',
):
    assert token in main, token

# Magazyn/QR: live hierarchy, stable scroll, photos in backup, real output actions.
for token in (
    'private final java.util.Map<String,Integer> screenScrollY',
    'children.setVisibility(nowCollapsed?View.GONE:View.VISIBLE);',
    'StorageThumbs.compress(',
    'getContentResolver(),data.getData()',
    'StorageThumbs.read(prefs,item.id)',
    'selectBulkQrLabels()',
    'StorageQrLabels.pdf(selected,format)',
    'StorageQrLabels.print(this,pdf,',
    'Drukuj przez Androida',
    'Zapisz jako PDF',
    'Udostępnij PDF',
):
    assert token in main, token
for token in ('storageThumbnails','storageQrHistory'):
    assert token in backup, token

# Tiles cannot be duplicated by Add/Edit, including hidden shortcuts.
for token in (
    'homeTargetAlreadyAdded(target,null)',
    'homeTargetAlreadyAdded(target,id)',
    'Przywróć ukryte kafelki',
):
    assert token in main, token

# CI must exercise all critical 0.6 gates before publishing.
for script in (
    'tests/check_bank_notification_contract.py',
    'tests/check_bank_auto_pending_contract.py',
    'tests/check_bank_statement_contract.py',
    'tests/check_paycheck_pending_contract.py',
    'tests/check_backup_roundtrip_contract.py',
    'tests/check_storage_qr_contract.py',
    'tests/check_storage_tree_contract.py',
    'tests/check_ui_continuity_and_thumbnails.py',
    'tests/check_home_tile_unique_contract.py',
):
    assert script in workflow, script

print("EDHOME 0.6 code-side release gate PASS; physical-phone acceptance still required")
