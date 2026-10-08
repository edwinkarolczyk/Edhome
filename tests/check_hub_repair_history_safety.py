#!/usr/bin/env python3
"""EDHOME: regresja kontraktowa, ponowne QR i konflikt bez baseline."""
from pathlib import Path

root=Path(__file__).resolve().parents[1]
android=(root/"app/src/main/java/com/edwinkarolczyk/edhome/DesktopHubSync.java").read_text(encoding="utf-8")
desktop=(root/"desktop/src/main/java/com/edhome/desktop/EdhomeDesktop.java").read_text(encoding="utf-8")
sql=(root/"app/src/main/java/com/edwinkarolczyk/edhome/PaycheckBudgetSqliteStore.java").read_text(encoding="utf-8")

def section(s,start,end):
    p=s.index(start)
    return s[p:s.index(end,p+len(start))]

pair=section(android,"static void pair(Context context,","static void resolveDesktop(Context context)")
assert "boolean sameDesktop=desktopId.equalsIgnoreCase(" in pair
assert "if(!sameDesktop) {" in pair
assert "baselineFile(context).delete();" in pair
assert "conflictFile(context).delete();" in pair
assert "if(!sameDesktop)editor.remove(PREF_CONFLICT);" in pair
assert pair.index("if(!sameDesktop) {")<pair.index("baselineFile(context).delete();")

sync=section(android,"private static void syncOnce(Context context)","private static void register(Context context")
missing=section(sync,"if(!baseline.isFile()) {","String base=readText(baseline)")
assert '"GET","/snapshot"' in missing
assert 'canonical(localRoot.opt("settings"))' in missing
assert 'canonical(localRoot.opt("tables"))' in missing
assert 'rememberConflict(context,prefs,' in missing
assert missing.index('rememberConflict(context,prefs,') < missing.index('applyServerSnapshot(context,prefs,server.body')
assert "HUB_NO_BASELINE_CONFLICT" in missing

resolve_phone=section(android,"static void resolvePhone(Context context)","private static void syncQuietly(Context context)")
assert 'if(!patch.isBlank())' in resolve_phone
assert 'saveConflictBackup(app,prefs);' in resolve_phone
assert '"X-EDHOME-BASE-SHA256",remoteSha' in resolve_phone
assert '"/snapshot",expectedLocal' in resolve_phone
assert 'if(result.code==409)' in resolve_phone
assert 'remote.sha256.isBlank()' in resolve_phone
assert 'applyServerSnapshot(app,prefs,result.body,result.sha256,expectedLocal)' in resolve_phone

resolve_desktop=section(android,"static void resolveDesktop(Context context)","static void resolvePhone(Context context)")
assert 'saveConflictBackup(app,prefs);' in resolve_desktop
assert 'applyServerSnapshot(app,prefs,result.body,result.sha256,expectedLocal)' in resolve_desktop

replace=section(desktop,"private String hubReplace(String incoming,String baseSha,","private String hubApplyPatch(")
assert 'saveHubBeforeFullReplace(current);' in replace
assert replace.index("DesktopBudgetIntegrity.assertSidecars(incomingRoot);") < replace.index("saveHubBeforeFullReplace(current);") < replace.index("snapshot=incomingRoot.deepCopy();")
assert 'Files.writeString(destination,original,StandardCharsets.UTF_8' in desktop
assert 'StandardOpenOption.CREATE_NEW' in desktop
assert 'DesktopDiagnosticLog.error("HUB_BACKUP_PRUNE"' in desktop

assert 'static boolean sameJson(Object a,Object b)' in sql
assert 'sameJson(json,new JSONObject(current.getString(1)))' in sql
assert 'new java.math.BigDecimal(a.toString()).compareTo(' in sql
assert 'if(left.length()!=right.length())return false;' in sql
print("PASS: QR zachowuje baseline, nowe PC wymaga decyzji, pełny CAS robi backup, historia porównywana semantycznie")
