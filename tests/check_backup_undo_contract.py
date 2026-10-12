#!/usr/bin/env python3
"""EDHOME P0: trwałe cofanie udanego restore + ochrona niewybranych przypomnień."""
from pathlib import Path
import subprocess
import tempfile

root = Path(__file__).resolve().parents[1]
src = root / "app/src/main/java/com/edwinkarolczyk/edhome"
archive = (src / "DataBackupArchive.java").read_text(encoding="utf-8")
ledger = (src / "BackupRecoveryLedger.java").read_text(encoding="utf-8")
main = (src / "MainActivity.java").read_text(encoding="utf-8")

for token in (
    "static File lastSuccessfulCheckpoint(Context context)",
    "BackupRecoveryLedger.lastCompleted(",
    "static void markSuccessfulRestore(Context context, File checkpoint)",
    "BackupRecoveryLedger.recordCompleted(",
    "markSuccessfulRestore(context, checkpoint);",
    "PaycheckBudgetSqliteStore.sameJson(",
):
    assert token in archive, "Brak ochrony cofania: " + token

for token in (
    "DataBackupArchive.lastSuccessfulCheckpoint(this)",
    "confirmDataBackupUndo(verified,false)",
    "confirmDataBackupUndo(choices[index],true)",
    "DataBackupArchive.recoveryCheckpoint(",
    "DataBackupArchive.restoreExact(",
    "DataBackupArchive.markSuccessfulRestore(this,redo)",
    "if(restoreCompleted&&redo!=null)",
    "boolean tasksAffected = selected==null",
    "boolean vehiclesAffected = selected==null",
    "boolean timersAffected = selected==null",
    "if((tasksAffected&&taskKey)",
    "||(vehiclesAffected&&vehicleKey)",
    "||(timersAffected&&timerKey))",
    "if(selected==null&&checkpoint!=null)",
    "DataBackupArchive.markSuccessfulRestore(this,checkpoint)",
):
    assert token in main, "Brak zabezpieczenia interfejsu: " + token

assert main.index("DataBackupArchive.lastSuccessfulCheckpoint(this)") < main.index(
    "confirmDataBackupUndo(verified,false)"
)
assert main.index("DataBackupArchive.markSuccessfulRestore(this,redo)") > main.index(
    "DataBackupArchive.restoreExact(\n                            this,db.getWritableDatabase(),prefs,selected)")
# Nowa instalacja ma puste edhome-recovery. Akcja Cofnij jest zawsze
# widoczna na górze ekranu, ale musi wyjaśniać brak punktu cofnięcia.
backup_screen = main.split('    private void backup() {', 1)[1].split(
    '    /** Prosty ekran aktualizacji', 1)[0]
for token in (
    'button("Przywróć kopię (.zip / starszy .json)"',
    'button("Cofnij ostatnie przywrócenie"',
    'DataBackupArchive.lastSuccessfulCheckpoint(this)',
    'Brak kopii ratunkowej. Przy nowej lub pustej instalacji',
    'Starsze kopie ratunkowe • wybierz świadomie',
):
    assert token in (backup_screen + main), (
        "Brak działania lub komunikatu dla pustej instalacji: " + token)
assert backup_screen.index('button("Cofnij ostatnie przywrócenie"') < (
    backup_screen.index('note("Jedna paczka ZIP v2'))
assert backup_screen.index('button("Przywróć kopię (.zip / starszy .json)"') < (
    backup_screen.index('button("Cofnij ostatnie przywrócenie"'))

assert "last-successful-restore.txt" in ledger
assert "output.getFD().sync()" in ledger
assert "!temporary.renameTo(marker)" in ledger
assert "checkpoint.getCanonicalFile().getParentFile().equals(" in ledger
assert "Nieudany import" in ledger

with tempfile.TemporaryDirectory(prefix="edhome-recovery-ledger-") as tmp:
    subprocess.run(
        ["javac", "-encoding", "UTF-8", "-d", tmp,
         str(src / "BackupRecoveryLedger.java"),
         str(root / "tests/task_rules/BackupRecoveryLedgerSmoke.java")],
        check=True, cwd=root)
    subprocess.run(
        ["java", "-cp", tmp,
         "com.edwinkarolczyk.edhome.BackupRecoveryLedgerSmoke"],
        check=True, cwd=root)

print("EDHOME P0: ostatni udany import, jawne stare kopie, powiadomienia "
      "wybranych modułów, semantyczny compare i Java smoke PASS")
