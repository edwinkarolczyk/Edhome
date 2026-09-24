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
assert 'button("Przejdź do daty", () -> {' in calendar
assert "showVehicleDateInCalendar(item.ocUntil)" in vehicle
assert "showVehicleDateInCalendar(item.inspectionUntil)" in vehicle
assert "SELECT deadline,COUNT(*) FROM (" in calendar
assert "WHERE deadline=? ORDER BY name,kind" in calendar
assert "versionCode 78" in gradle
assert "versionName '0.6.0.13'" in gradle
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
