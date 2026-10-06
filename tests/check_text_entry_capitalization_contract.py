from pathlib import Path

root=Path(__file__).resolve().parents[1]
main=(root/"app/src/main/java/com/edwinkarolczyk/edhome/MainActivity.java").read_text(encoding="utf-8")
rules=(root/"app/src/main/java/com/edwinkarolczyk/edhome/TextEntryRules.java").read_text(encoding="utf-8")
project=(root/"app/src/main/java/com/edwinkarolczyk/edhome/ProjectStore.java").read_text(encoding="utf-8")
storage=(root/"app/src/main/java/com/edwinkarolczyk/edhome/StorageStore.java").read_text(encoding="utf-8")
users=(root/"app/src/main/java/com/edwinkarolczyk/edhome/UserProfileStore.java").read_text(encoding="utf-8")
shopping=(root/"app/src/main/java/com/edwinkarolczyk/edhome/ShoppingRules.java").read_text(encoding="utf-8")

assert "capitalizeLabel(String raw)" in rules
assert "Character.toUpperCase(ch)" in rules
assert "TYPE_TEXT_FLAG_CAP_SENTENCES" in main
for marker in (
    "title = TextEntryRules.capitalizeLabel(title);",
    "name = TextEntryRules.capitalizeLabel(name);",
):
    assert marker in main
for source in (project,storage,users,shopping):
    assert "TextEntryRules.capitalizeLabel" in source

print("text entry capitalization contract OK")
