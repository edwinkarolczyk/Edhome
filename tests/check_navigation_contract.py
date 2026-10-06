#!/usr/bin/env python3
"""Kontrakt nawigacji EDHOME: Cofnij zachowuje kontekst, Start pozostaje osobnym celem."""
from pathlib import Path

main=Path("app/src/main/java/com/edwinkarolczyk/edhome/MainActivity.java").read_text(encoding="utf-8")

# Nagłówek modułu nie może wyrzucać na pulpit.
assert 'text("← Cofnij", 12, true)' in main
assert 'text("← Start", 12, true)' not in main
assert 'back.setOnClickListener(v -> goBack());' in main

# Prawdziwa historia nawigacji z limitem zamiast łańcucha hardcoded ekranów.
for marker in (
    "static final class NavState",
    "NAV_HISTORY_LIMIT=48",
    "ArrayDeque<NavState> navigationHistory",
    "pushNavigationState()",
    "navigationHistory.addLast(state)",
    "navigationHistory.removeLast()",
    "private void goBack()",
    "private void goHome()",
    "navigateTo(destination,true)",
    "NAV_BACK",
):
    assert marker in main, marker

# Cofnięcie przywraca istotny kontekst widoku i scroll.
for marker in (
    "selectedProjectId=state.selectedProjectId",
    "selectedMemberId=state.selectedMemberId",
    "tasksFilter=state.tasksFilter",
    "tasksPage=state.tasksPage",
    "pantrySearch=state.pantrySearch",
    "pantryPage=state.pantryPage",
    "pantryCategoryFilter=state.pantryCategoryFilter",
    "calendarMonth=state.calendarMonth",
    "calendarDay=state.calendarDay",
    "calendarView=state.calendarView",
    "screenScrollY.put(state.screen,state.scrollY)",
):
    assert marker in main, marker

# Projekty/podprojekty też odkładają poprzedni poziom na stos.
assert "private void openProject(long projectId)" in main
assert "pushNavigationState();" in main.split(
    "private void openProject(long projectId)",1)[1].split(
    "private void projectBack",1)[0]
assert "box.setOnClickListener(v->openProject(project.id));" in main
assert "projectBack(project)" in main

# Fizyczny Back używa tej samej logiki.
back=main.split("@Override public void onBackPressed()",1)[1].split(
    "private int dp(float d)",1)[0]
assert "goBack();" in back
assert 'go("home")' not in back

# Start w dolnej nawigacji jest osobnym, świadomym przejściem do pulpitu.
assert main.count("this::goHome") >= 3
assert 'go("home")' not in main

# Każdy callback z pracy w tle/NFC do UI przechodzi przez wspólną ochronę
# przed WindowManager/Activity callbacks po zniszczeniu ekranu.
assert "private void runOnLiveUi(Runnable action)" in main
helper=main.split("private void runOnLiveUi(Runnable action)",1)[1].split(
    "@Override protected void onStart()",1)[0]
assert "if (isFinishing() || isDestroyed()) {" in helper
assert "if(unavailable!=null)unavailable.run();" in helper
destroyed_guard=helper.split("if (isFinishing() || isDestroyed()) {",1)[1].split("}",1)[0]
assert "return;" in destroyed_guard
assert "runOnUiThread(() ->" in helper
assert main.count("runOnUiThread(")==1
assert main.count("runOnLiveUi(")>=19

# Start/Stop pracy nad czynnością nadal istnieją i nie są nawigacją.
assert "ProjectStore.startWork" in main
assert "ProjectStore.stopWork" in main

print("Navigation back-stack/context contract: PASS")
