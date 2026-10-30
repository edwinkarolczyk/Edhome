#!/usr/bin/env python3
"""Magazyn: place tree indentation and globally continuous quick Thing numbering."""
from pathlib import Path

main = Path("app/src/main/java/com/edwinkarolczyk/edhome/MainActivity.java").read_text(encoding="utf-8")

branch = main.split("private void renderPlaceBranch(", 1)[1].split(
    "private void showPlaceDeleteBlocked(", 1
)[0]
assert "branchParams.setMargins(dp(depth * 20), dp(3), 0, dp(3));" in branch
assert '"↳  Poziom " + (depth + 1)' in branch
assert "renderPlaceBranch(child, all, drawn, depth + 1);" in branch

numbering = main.split("private int nextQuickThingBatchNumber()", 1)[1].split(
    "private void startQuickThingBatch()", 1
)[0]
assert "FROM storage_items WHERE kind='thing'" in numbering
assert "name GLOB 'Rzecz [0-9]*'" in numbering
assert 'value.matches("Rzecz [0-9]+")' in numbering
assert "return maximum + 1;" in numbering

start = main.split("private void startQuickThingBatch()", 1)[1].split(
    "private void showQuickThingBatchTargetChooser()", 1
)[0]
assert "quickThingBatchCounter=nextQuickThingBatchNumber();" in start

commit = main.split("private void quickThingBatchCommitPhoto(", 1)[1].split(
    "private void showQuickThingBatchAfterPhoto(", 1
)[0]
assert "quickThingBatchCounter=Math.max(" in commit
assert "quickThingBatchCounter,nextQuickThingBatchNumber()" in commit
assert 'String name="Rzecz "+quickThingBatchCounter;' in commit

print("Place tree indentation and global serial Thing numbering: PASS")
