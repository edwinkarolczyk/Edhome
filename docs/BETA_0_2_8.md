# EDHOME 0.2.8-beta.1 — tygodniowy grafik i wyjątki domowników

## Zrealizowane na beta

- Beta nadal **bez PIN-u** na starcie, po powrocie z tła i imporcie; Stable zachowuje PIN.
- `Czynności → Domownicy → Grafik i wyjątki` osobno dla każdej osoby.
- Tygodniowy plan poniedziałek–niedziela. Dostępne stany: **Nie ustawiono**, **Wolne**, **06:00–14:00**, **14:00–22:00**, **22:00–06:00** (nocna kończy się następnego dnia). Nieustalone to nie to samo co Wolne; program nie wymyśla grafiku domownika.
- Wyjątek na wybraną datę nadpisuje dany dzień tygodnia. Usunięcie wyjątku przywraca regułę tygodniową, nawet gdy nadpisana była jako Wolne. Lista zapisanych wyjątków ma osobne usuwanie.
- Usunięcie domownika odłącza go od zadań i w tej samej transakcji usuwa jego tygodniowy grafik i wyjątki.
- SQLite **v7** dodaje `member_weekly_shifts` i `member_shift_exceptions`, nie zmienia starych zadań, miejsc, osób ani historii. Backup JSON `databaseVersion=7` przechowuje grafik i wyjątki, sprawdza daty, powielone dni i nieistniejących domowników; kopie v2–v6 mogą zostać wczytane z pustym grafikiem.

### Czego tu jeszcze nie ma

Nie ma jeszcze generowania trzech propozycji terminu, rotacji 6/14 automatycznie co tydzień, własnych godzin zmian lub integracji z kalendarzem innych osób. To podstawowy **lokalny grafik i wyjątki**, nie automatyczny planer. Następny etap ma dopiero wykorzystać jawnie wpisaną dostępność.

## Test odbioru

1. Zrób kopię JSON na zewnątrz EDHOME i zaktualizuj 0.2.7→0.2.8 na telefonie bez odinstalowania.
2. Wejdź w Czynności → Domownicy; ustaw poniedziałek 06–14, wtorek 14–22, środę Wolne i pozostaw czwartek Nie ustawiono. Zamknij, uruchom aplikację i potwierdź trwałość każdego stanu.
3. Wybierz poniedziałkową datę i dodaj wyjątek Wolne, potem usuń: powraca zmiana 06–14, nie znika grafik całego tygodnia.
4. Ustaw nocną 22–06; interfejs ma informować, że kończy się kolejnego dnia. Wyjątek zapisany na konkretną datę ma być oddzielny od reguły dnia tygodnia.
5. Przypisz osobę do zadania, usuń osobę; zadanie i jego historia zostają, grafik i wyjątki znikają.
6. Wyeksportuj i testowo przywróć v7; sprawdź także starszą v6. Nie testuj destrukcyjnego importu na jedynej kopii danych.

Build, podpis i testy SQLite w CI **nie zastępują testu na fizycznym Androidzie**.
