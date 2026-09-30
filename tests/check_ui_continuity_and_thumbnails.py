#!/usr/bin/env python3
"""UX acceptance: stable viewport, live preview and locally backed-up thumbnails."""
from pathlib import Path
src=Path("app/src/main/java/com/edwinkarolczyk/edhome")
main=(src/"MainActivity.java").read_text(encoding="utf-8")
backup=(src/"DataBackup.java").read_text(encoding="utf-8")
thumb=(src/"StorageThumbs.java").read_text(encoding="utf-8")
beta=Path("app/src/beta/AndroidManifest.xml").read_text(encoding="utf-8")
assert 'private final java.util.Map<String,Integer> screenScrollY' in main
assert 'screenScrollY.put(screen,pageScroll.getScrollY());' in main
assert 'scroll.post(()->{' in main and 'scroll.scrollTo(0,Math.min(restoreScrollY,maxY));' in main
start=main.index('private LinearLayout storageTreeHeading(')
end=main.index('private void storageTreePlace(',start)
assert 'children.setVisibility(nowCollapsed?View.GONE:View.VISIBLE);' in main[start:end]
assert 'render();' not in main[start:end]
assert 'getInstalledApplications(0)' in main
assert 'Intent.ACTION_PICK_ACTIVITY' in main
assert 'PICK_BANK_APP_SYSTEM' in main
assert 'getPackageManager().queryIntentActivities(launcher,0)' in main.replace('\n                .queryIntentActivities','\ngetPackageManager().queryIntentActivities') or 'queryIntentActivities(launcher,0)' in main
assert 'setView(form)' in main
# The bank picker must not revert to a 234-row native multi-choice list.
# Magazyn QR batch printing intentionally uses native multi-selection.
bank=main.split('private void configureBankNotifications()',1)[1].split('private void showBankNotificationHints()',1)[0]
assert 'setMultiChoiceItems(names,selected' not in bank
assert 'IMPORT_STORAGE_THUMBNAIL' in main
assert 'StorageThumbs.compress(' in main and 'getContentResolver(),data.getData()' in main
assert 'StorageThumbs.read(prefs,item.id)' in main
assert 'StorageThumbs.key(item.id)' in main
assert 'StorageThumbs.MAX_JPEG_BYTES' in backup
assert 'storageThumbnails' in backup
assert 'restoredStorageThumbs' in backup
assert 'presentStorageIds.contains(id)' in backup
assert 'restored.putString(StorageThumbs.key(thumb.getKey())' in backup
assert 'android.graphics.BitmapFactory' in thumb
assert 'Bitmap.CompressFormat.JPEG' in thumb
assert 'ACTION_OPEN_DOCUMENT' in main and 'picker.setType("image/*")' in main
assert 'BankNotificationListener' in beta
print("UX: preserved viewport, inline Magazyn preview, bank selector and photo backup PASS")
