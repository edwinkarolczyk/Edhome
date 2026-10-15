#!/usr/bin/env python3
"""Beta launcher: jeden skrót, wspólny zapis, walidacja, obsługa zimnego i ciepłego startu."""
from pathlib import Path
import xml.etree.ElementTree as ET

android="{http://schemas.android.com/apk/res/android}"
overlay=ET.parse("app/src/beta/AndroidManifest.xml").getroot()
entries=ET.parse("app/src/beta/res/xml/shortcuts.xml").getroot().findall("shortcut")
assert len(entries)==2
assert {item.attrib[android+"shortcutId"] for item in entries}=={"quick_task","quick_nfc"}
quick_task=next(item for item in entries if item.attrib[android+"shortcutId"]=="quick_task")
quick_nfc=next(item for item in entries if item.attrib[android+"shortcutId"]=="quick_nfc")
assert quick_nfc.attrib[android+"shortcutShortLabel"]=="@string/shortcut_nfc_short"
nfc_intent=quick_nfc.find("intent")
assert nfc_intent is not None
assert nfc_intent.attrib[android+"targetPackage"]=="com.edwinkarolczyk.edhome.beta"
assert nfc_intent.attrib[android+"targetClass"]=="com.edwinkarolczyk.edhome.NfcShortcutScannerActivity"
assert nfc_intent.attrib[android+"action"]=="com.edwinkarolczyk.edhome.action.QUICK_NFC"
assert quick_task.attrib[android+"shortcutId"]=="quick_task"
assert quick_task.attrib[android+"shortcutShortLabel"]=="@string/shortcut_quick_task_short"
intent=quick_task.find("intent")
assert intent is not None
assert intent.attrib[android+"targetPackage"]=="com.edwinkarolczyk.edhome.beta"
assert intent.attrib[android+"targetClass"]=="com.edwinkarolczyk.edhome.MainActivity"
assert intent.attrib[android+"action"]=="com.edwinkarolczyk.edhome.action.QUICK_TASK"
entry=overlay.find("./application/activity")
assert entry.attrib[android+"launchMode"]=="singleTask"
scan_entry=overlay.find("./application/activity[@"+android+"name"+'=".NfcShortcutScannerActivity"]')
assert scan_entry is not None and scan_entry.attrib[android+"exported"]=="true"
assert "android.permission.BIND_NOTIFICATION_LISTENER_SERVICE" in Path(
    "app/src/beta/AndroidManifest.xml").read_text(encoding="utf-8")
assert overlay.find("./application/service[@"+android+"name"+'=".BankNotificationListener"]') is not None
assert overlay.find("./application/service[@"+android+"name"+'=".LanSyncService"]') is not None
assert len(overlay.findall("./application/provider")) == 2
assert entry.find("meta-data").attrib[android+"resource"]=="@xml/shortcuts"
assert not Path("app/src/stable/res/xml/shortcuts.xml").exists()
main=Path("app/src/main/java/com/edwinkarolczyk/edhome/MainActivity.java").read_text(encoding="utf-8")
form=Path("app/src/main/java/com/edwinkarolczyk/edhome/QuickTaskShortcut.java").read_text(encoding="utf-8")
for token in ("root.post(() -> openLauncherQuickTask(getIntent()));",
              "if (openLauncherQuickTask(intent)) return;",
              "intent.setAction(Intent.ACTION_MAIN);",
              'db.saveTask(null, title, due, "once", 1,',
              "ReminderReceiver.schedule(this);",
              'DiagnosticLog.event("TASK_QUICK_SHORTCUT_ADDED");'):
    assert token in main, token
for token in ('"Dzisiaj", "Jutro", "Bez terminu", "Wybierz datę…"',
              'TaskRules.validate(name, due, "once", 1)',
              "onSave.save(name, due);", "dialog.dismiss();",
              "title.requestFocus();", "SOFT_INPUT_STATE_ALWAYS_VISIBLE"):
    assert token in form,token
gradle=Path("app/build.gradle").read_text(encoding="utf-8")
assert "versionCode 306" in gradle and "versionName '0.8.0.114'" in gradle
scanner=Path("app/src/beta/java/com/edwinkarolczyk/edhome/NfcShortcutScannerActivity.java").read_text(encoding="utf-8")
for token in (
    "NfcAdapter.getDefaultAdapter(this)", "adapter.isEnabled()",
    "adapter.enableReaderMode(", "adapter.disableReaderMode(this)",
    "NfcAdapter.FLAG_READER_SKIP_NDEF_CHECK", "SCAN_WINDOW_MS = 7_000L",
    "handler.postDelayed(timeout, SCAN_WINDOW_MS)", "NfcAdapter.ACTION_TAG_DISCOVERED",
    "NfcAdapter.EXTRA_TAG", "MainActivity.class", "void onPause()", "stopScan();"
):
    assert token in scanner,token
assert "android.nfc.action.TAG_DISCOVERED" in Path("app/src/main/AndroidManifest.xml").read_text(encoding="utf-8")
print("Launcher Beta: jeden skrót, zapis istniejących czynności, dialog i regresja OK")
