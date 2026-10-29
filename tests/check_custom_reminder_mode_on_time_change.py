from pathlib import Path

root = Path(__file__).resolve().parents[1]
main = (root / "app/src/main/java/com/edwinkarolczyk/edhome/MainActivity.java").read_text(encoding="utf-8")

# Picking a specific reminder time must immediately select custom reminder mode.
marker = 'reminderHour.setOnClickListener(v -> {'
start = main.index(marker)
end = main.index('form.addView(text("Godzina przypomnienia"', start)
picker = main[start:end]
assert 'reminderHour.setText(String.format' in picker
assert 'reminderMode.setSelection(1);' in picker
assert picker.index('reminderHour.setText(String.format') < picker.index('reminderMode.setSelection(1);')
# Saving must use the selected mode so custom time is persisted and re-scheduled.
assert 'String customTime = reminderMode.getSelectedItemPosition() == 0' in main
assert 'reminderMode.getSelectedItemPosition() == 0\n                    ? null : reminderHour.getText().toString()' in main
print("PASS: choosing a reminder time immediately selects custom mode; save persists the selected mode.")
