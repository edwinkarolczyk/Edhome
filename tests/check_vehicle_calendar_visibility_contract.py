#!/usr/bin/env python3
"""0.6.0.3: vehicle deadlines beyond the agenda's 30-day horizon are discoverable."""
from pathlib import Path
import sqlite3

main=Path("app/src/main/java/com/edwinkarolczyk/edhome/MainActivity.java").read_text(encoding="utf-8")
gradle=Path("app/build.gradle").read_text(encoding="utf-8")
section=main.split("private void upcomingVehicleDeadlines()",1)[1].split(
    "private void calendar()",1)[0]
jump=main.split("private void showVehicleDateInCalendar(String date)",1)[1].split(
    "private void upcomingVehicleDeadlines()",1)[0]
calendar=main.split("private void calendar()",1)[1].split(
    "private void vehicles()",1)[0]
vehicle=main.split("private void vehicles()",1)[1].split(
    "private Spinner tyrePlaceSpinner(",1)[0]

for token in (
    "calendarDay = day.toString();",
    "calendarMonth = YearMonth.from(day).toString();",
    'calendarView = "day";',
    'go("calendar");',
):
    assert token in jump, token
for token in (
    '"SELECT oc_until AS deadline,\'OC\' AS kind,name FROM vehicles "',
    '"SELECT inspection_until,\'Przegląd\',name FROM vehicles "',
    '"WHERE inspection_until!=\'\') WHERE deadline>=? "',
    '"ORDER BY deadline,name,kind LIMIT 20";',
    "showVehicleDateInCalendar(date)",
    "LocalDate.now().toString()",
):
    assert token in section,token
assert "upcomingVehicleDeadlines();" in calendar
assert 'if ("month".equals(calendarView)) {' in calendar
assert "renderCalendarMonth(month, selected);" in calendar
assert "private boolean changeCalendarMonth(int delta)" in main
assert 'DiagnosticLog.event("CALENDAR_MONTH_SWIPED"' in main
assert "calendarMonthSwipeTracking" in main
assert 'calendarView = "day";' in main
assert "calendarMonthFrame = new android.widget.FrameLayout(this)" in main
assert "fullCalendarMonth" in main
assert "Gravity.BOTTOM | Gravity.RIGHT" in main
assert "calendarMonthFrame.addView(add, addParams)" in main
assert "selectedCard = card()" not in main
assert "weekdayHeadings" in main
assert 'headingIndex==6 ? sundayColor : subdued' in main
assert "isSunday = day.getDayOfWeek()==java.time.DayOfWeek.SUNDAY" in main
assert "(isSunday ? sundayColor : ink)" in main
assert "isSelected ? skin.iconBacking : surface" not in main
assert "target.atDay(wantedDay)" not in main
assert 'text("‹  przesuń miesiąc palcem  ›"' not in main
for token in (
    "private void renderCalendarMonth(YearMonth month, LocalDate selected)",
    "calendarMonthEventBar",
    "WEEK_OF_WEEK_BASED_YEAR",
    '"pon.", "wt.", "śr.", "czw.", "pt.", "sob.", "niedz."',
    '"SELECT due_date,title,priority FROM tasks WHERE done=0 "',
):
    assert token in main, token
assert 'button("Przejdź do daty", () -> {' in calendar
assert "showVehicleDateInCalendar(item.ocUntil)" in vehicle
assert "showVehicleDateInCalendar(item.inspectionUntil)" in vehicle
assert "SELECT deadline,COUNT(*) FROM (" in calendar
assert "WHERE deadline=? ORDER BY name,kind" in calendar
assert int(__import__("re").search(r"\bversionCode\s+(\d+)", gradle).group(1)) >= 84
assert __import__("re").search(r"versionName '0\.(?:6\.0|7\.\d+|8\.\d+\.\d+)\.\d+'", gradle) is not None
assert "versionNameSuffix ''" in gradle

db=sqlite3.connect(":memory:")
db.execute("CREATE TABLE vehicles(name TEXT,oc_until TEXT,inspection_until TEXT)")
db.execute("INSERT INTO vehicles VALUES(?,?,?)",("Audi A4","2027-09-08","2027-04-08"))
db.execute("INSERT INTO vehicles VALUES(?,?,?)",("Auto B","2026-09-24",""))
sql=("SELECT deadline,kind,name FROM ("
     "SELECT oc_until AS deadline,'OC' AS kind,name FROM vehicles "
     "WHERE oc_until!='' UNION ALL "
     "SELECT inspection_until,'Przegląd',name FROM vehicles "
     "WHERE inspection_until!='') WHERE deadline>=? "
     "ORDER BY deadline,name,kind LIMIT 20")
rows=db.execute(sql,("2026-09-23",)).fetchall()
assert rows==[
    ("2026-09-24","OC","Auto B"),
    ("2027-04-08","Przegląd","Audi A4"),
    ("2027-09-08","OC","Audi A4"),
],rows
assert db.execute("SELECT COUNT(*) FROM vehicles WHERE oc_until='2027-09-08'").fetchone()==(1,)
print("Beyond-30-day vehicle deadlines, date jump, month counts, no duplicate records: PASS")
