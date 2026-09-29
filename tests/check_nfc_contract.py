from pathlib import Path

root=Path(__file__).resolve().parents[1]
src=root/"app/src/main/java/com/edwinkarolczyk/edhome"
main=(src/"MainActivity.java").read_text(encoding="utf-8")
store=(src/"NfcLinkStore.java").read_text(encoding="utf-8")
storage=(src/"StorageStore.java").read_text(encoding="utf-8")
vehicle=(src/"VehicleStore.java").read_text(encoding="utf-8")
manifest=(root/"app/src/main/AndroidManifest.xml").read_text(encoding="utf-8")
backup=(src/"DataBackup.java").read_text(encoding="utf-8")

for token in (
    'android.permission.NFC',
    'android.hardware.nfc',
    'android:required="false"',
    'android.nfc.action.TAG_DISCOVERED',
):
    assert token in manifest, token

for token in (
    'NfcAdapter.getDefaultAdapter(this)',
    'enableReaderMode',
    'disableReaderMode',
    'handleNfcIntent',
    'handleNfcUid',
    'Przypisz tag NFC',
    'Zmień tag NFC',
    'Usuń powiązanie NFC',
    'NFC_TAG_ASSIGNED',
    'NFC_TAG_UNLINKED',
    'NFC_TARGET_SCANNED',
    'Przypisać teraz tag NFC?',
    'beginNfcAssignment(kind,newId,newName)',
):
    assert token in main, token

# The central Scanner explicitly rearms the same ReaderMode used by object
# assignment. It must not cover the reader with a modal "scanner active" prompt.
for token in (
    'private void armScannerNfc()',
    'disableNfcReaderMode();',
    'if(enableNfcReaderMode())',
    'if ("scanner".equals(destination)) armScannerNfc();',
    'scannerNfcStatus="NFC aktywne — przyłóż naklejkę albo brelok."',
    'scannerNfcStatus="✓ Odczytano NFC • "+NfcLinkStore.shortUid(uid)',
    'nfcState.addView(text(scannerNfcStatus, 15, true));',
):
    assert token in main, "Central NFC scanner rearm/status missing: "+token
assert 'alert("Skaner NFC jest aktywny.' not in main

# A known NFC tag now enters the central Scanner routing. It may show actions
# or open immediately according to the saved per-object/type/global rule.
assert 'handleKnownNfcScan(current);' in main
assert 'resolveScanAction("nfc", link.kind, link.targetId)' in main
assert 'showScannedTargetActions("nfc", link.kind, link.targetId, name)' in main

# Each agreed target is exposed in the object UI.
for token in (
    'nfcTargetButton(box,"place",entry.id,entry.name)',
    'nfcTargetButton(details,item.kind,item.id,item.name)',
    'nfcTargetButton(box,"pantry",id,name)',
    'nfcTargetButton(box,"vehicle",id,item.name)',
):
    assert token in main, token

# UID mapping is local, singular and safe to replace.
for token in (
    'final class NfcLinkStore',
    'findByUid',
    'findByTarget',
    'targetExists',
    'db.delete("nfc_links","target_kind=? AND target_id=?"',
    'db.delete("nfc_links","uid=? COLLATE NOCASE"',
    'db.insertOrThrow("nfc_links",null,row)',
):
    assert token in store, token

# Removing an object must not leave a stale NFC pointer.
assert 'NfcLinkStore.clearTarget(db,item.kind,item.id);' in storage
assert 'NfcLinkStore.clearTarget(db,"vehicle",id);' in vehicle
assert 'NfcLinkStore.clearTarget(database,"place",id);' in main
assert 'NfcLinkStore.clearTarget(database,"pantry",id);' in main

# NFC survives portable backup / restore.
assert '{"nfc_links", "id", "uid", "target_kind", "target_id", "created_at"}' in backup

print("EDHOME Android NFC object binding: PASS")
