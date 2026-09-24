#!/usr/bin/env python3
"""No duplicate module in Add/Edit tile, including hidden target slots."""
from pathlib import Path
s=Path("app/src/main/java/com/edwinkarolczyk/edhome/MainActivity.java").read_text(encoding="utf-8")
start=s.index("private boolean homeTargetAlreadyAdded(String target,String excludedTileId)")
end=s.index("private void restoreHiddenHomeTile()",start)
selection=s[start:end]
assert "for(String tileId:allHomeTiles())" in selection
assert "tileId.equals(excludedTileId)" in selection
assert "homeTileTarget(tileId).equals(target)" in selection
assert "homeTargetAlreadyAdded(target,null)" in selection
assert 'if(targets.isEmpty())' in selection
assert "java.util.List<String> order = allHomeTiles();" in selection
assert "order.add(id);" in selection
assert "homeTargetAlreadyAdded(target,null)" in selection.split("setItems(")[1]
assert 'Przywróć ukryte kafelki' in selection
editor=s[s.index("private void editHomeTile(String id)"):s.index("private java.util.List<String> allHomeTiles()")]
assert "homeTargetAlreadyAdded(target,id)" in editor
assert "String target = targets.get(destination.getSelectedItemPosition());" in editor
assert "&& homeTargetAlreadyAdded(target,id))" in editor
assert "target.equals(homeTileTarget(id))" in editor
assert "Ten moduł jest już przypisany" in editor
print("Home tiles: add/edit rejects duplicate targets including hidden, existing tiles retained PASS")
