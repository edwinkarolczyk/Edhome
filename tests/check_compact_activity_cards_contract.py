from pathlib import Path

source = Path("app/src/main/java/com/edwinkarolczyk/edhome/MainActivity.java").read_text(encoding="utf-8")
start = source.index("private void drawTask(")
end = source.index("private void taskAction(", start)
card = source[start:end]

required = {
    "reduced card padding": "box.setPadding(dp(9), dp(5), dp(9), dp(6));",
    "smaller checkbox": "new LinearLayout.LayoutParams(dp(38),dp(38))",
    "smaller activity title": "TextView taskTitle=text(name,15,true);",
    "metadata limited to two lines": "meta.setMaxLines(2);",
    "long metadata visually truncated": "meta.setEllipsize(android.text.TextUtils.TruncateAt.END);",
    "full metadata retained for accessibility": "meta.setContentDescription(description);",
}
missing = [label for label, snippet in required.items() if snippet not in card]
if missing:
    raise SystemExit("Compact activity card contract failed: " + ", ".join(missing))
print("Compact activity card contract: PASS")
