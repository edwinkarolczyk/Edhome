#!/usr/bin/env python3
"""100-icon 3D pack stays opt-in, local, bounded and separate from vault/ledger."""
from pathlib import Path
root = Path("app/src/main/java/com/edwinkarolczyk/edhome")
pack = (root / "IconPack3D.java").read_text(encoding="utf-8")
ui = (root / "MainActivity.java").read_text(encoding="utf-8")
crypto = (root / "PrivatePaycheckCrypto.java").read_text(encoding="utf-8")
portable = (root / "PrivatePaycheckPortable.java").read_text(encoding="utf-8")
for token in (
    'EXPECTED = 100', 'manifest.json', 'icons/', 'sha256(icon)',
    'EXPECTED + 1', 'entrySize > MAX_ENTRY', 'total > MAX_EXTRACTED',
    'BUNDLED_ASSET = "EDHOME_AI_3D_100.zip"', 'installBundled(Context context)',
    'getAssets().open(BUNDLED_ASSET)',
    'options.outWidth != 192', 'stage.renameTo(active)',
    'previous.renameTo(active)', 'new File(context.getFilesDir()',
    'static String defaultFor(String target)', 'PREFIX = "ai3d_"',
):
    assert token in pack, token
for token in (
    'IMPORT_AI_3D_PACK = 1220', 'AI3D_BUNDLED_INSTALLED',
    'IconPack3D.installBundled(this)',
    'IconPack3D.importArchive(this, chosen)', 'tileIconImage(',
    'icon_style', 'IconPack3D.options(this)', 'IconPack3D.known(this, iconId)',
):
    assert token in ui, token
assert 'password.length >= 5' in crypto
assert 'password.length >= 12' in crypto
assert 'validBackupPassword(backupPassword)' in portable
assert 'validBackupPassword(password)' in ui
assert 'validBackupPassword(backupPass)' in ui
print("AI 3D import and 5-char vault / 12-char backup separation: PASS")
