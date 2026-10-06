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
assert 'StorageThumbs.compress(' in main
assert 'Uri selected=data.getData()' in main and 'getContentResolver(),selected' in main
assert 'StorageOriginals.save(this,id,selected)' in main
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

assert 'static int prune(SharedPreferences prefs,SQLiteDatabase db)' in thumb
assert 'SELECT id FROM storage_items' in thumb
assert 'StorageThumbs.prune(' in main
service=(src/"LanSyncService.java").read_text(encoding="utf-8")
assert 'STORAGE_THUMBNAILS_PRUNED_AFTER_SYNC' in service
assert 'StorageThumbs.prune(' in service
print("Storage thumbnail orphan cleanup after startup/sync PASS")

# Jakość + Low RAM: pełne zdjęcie zostaje jako oryginał, a miniatura ma
# mały profil renderowania z downsamplingiem i ograniczonym cache.
assert 'static final int MAX_EDGE_PX=384;' in thumb
assert 'static final int LOW_RAM_EDGE_PX=256;' in thumb
assert 'ActivityManager' in thumb and 'isLowRamDevice()' in thumb
assert 'LruCache<Long,CachedBitmap>' in thumb
assert 'sample.inSampleSize*=2' in thumb
assert 'renderEdgePx' in thumb
assert 'StorageThumbs.configure(this);' in main
assert 'StorageThumbs.trimMemory(level);' in main
assert 'THUMBNAIL_CACHE_TRIM' in thumb
assert 'static final int MAX_JPEG_BYTES=256*1024;' in thumb
assert 'static final int MAX_BASE64_CHARS=360000;' in thumb
assert 'for(int quality:new int[]{92,88,84,80,76,72,68})' in thumb
assert '192.0/Math.max(w,h)' not in thumb
assert 'FileProvider.getUriForFile' in main
assert 'android.provider.MediaStore.EXTRA_OUTPUT' in main
assert 'STORAGE_THUMBNAIL_FULLRES_SAVED' in main
assert 'data.getExtras().get("data")' not in main[
    main.index('if (request == TAKE_STORAGE_THUMBNAIL)'):
    main.index('if (request == IMPORT_STORAGE_THUMBNAIL)')
]
manifest=Path("app/src/main/AndroidManifest.xml").read_text(encoding="utf-8")
assert 'androidx.core.content.FileProvider' in manifest
assert '@xml/storage_file_paths' in manifest
print("Storage thumbnails: full-resolution original + 384/256px Low RAM cache PASS")

# Podmiana oryginalnego zdjęcia musi zachować poprzedni plik aż do poprawnego
# zainstalowania nowego oraz odtworzyć go po przerwanym/nieudanym zapisie.
originals=(src/"StorageOriginals.java").read_text(encoding="utf-8")
assert 'File backup = new File(directory, itemId + ".bak");' in originals
assert '!destination.exists() && backup.exists() && !backup.renameTo(destination)' in originals
assert 'if (!destination.renameTo(backup))' in originals
assert 'if (!temporary.renameTo(destination))' in originals
assert 'if (previousMoved && !backup.renameTo(destination))' in originals
assert 'Stare zdjęcie zachowano.' in originals
assert 'destination.exists() && !destination.delete()' not in originals
print("Storage originals: crash-safe photo replacement with rollback PASS")


# Diagnostyka wydajności nie może zniknąć: wolne rendery zapisują ekran,
# czas, heap, liczbę widoków i profil Low RAM bez dodatkowej usługi w tle.
assert 'final long renderStartedNs=System.nanoTime();' in main
assert 'UI_RENDER_PERF' in main
assert 'heapMb=' in main
assert 'views=' in main
assert 'countViewTree(root,4000)' in main
assert 'renderMs>=40L' in main
assert 'now-lastRenderPerfLogAt>=30000L' in main
print("Performance diagnostics: render/heap/view-count/Low-RAM telemetry PASS")


# Low RAM ogranicza koszt animatorów pulpitu, ale zachowuje ten sam układ docelowy.
assert 'if(StorageThumbs.isLowRamDevice()) {' in main
assert 'tiles.setTranslationX(0f);' in main
assert 'tile.setTranslationX(target[0] - old[0]);' in main
assert 'tile.setTranslationY(target[1] - old[1]);' in main
print("Low RAM: reduced home animations PASS")


# Magazyn nie może wrócić do N+1: ekran pobiera wszystkie rekordy jednym
# zapytaniem, a dopiero pojedyncze akcje/szczegóły korzystają z find().
storage_store=(src/"StorageStore.java").read_text(encoding="utf-8")
storage_screen=main.split("private void storage() {",1)[1].split(
    "private boolean storageThingsVisible()",1)[0] if "private boolean storageThingsVisible()" in main else main.split(
    "private void storage() {",1)[1].split("private void storageViewSwitcher()",1)[0]
assert "static List<Item> listAll(SQLiteDatabase db)" in storage_store
assert "StorageStore.listAll(db.getReadableDatabase())" in storage_screen
assert 'SELECT id FROM storage_items ORDER BY kind,name COLLATE NOCASE,id' not in storage_screen
assert "storage_items_kind_name_idx" in storage_store
assert "storage_items_place_idx" in storage_store
assert "StorageStore.ensurePerformanceIndexes(database);" in main
print("Storage performance: batch inventory load + persistent indexes PASS")
