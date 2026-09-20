# EDHOME 0.1.5-beta.1 — czynności i kalendarz

**Wariant Beta DEV; versionCode 6, SQLite v3.** Kod i kompilację należy odróżniać od instalacji oraz testu na konkretnym telefonie.

## Co dodano

- Samodzielne czynności: nazwa, opcjonalna data, edycja, wykonanie, wznowienie jednorazowych i usuwanie.
- Cykl: jednorazowo, codziennie, co tydzień/miesiąc/rok, co N dni/tygodni/miesięcy/lat oraz przed wiosną/latem/jesienią/zimą.
- Przy regułach sezonowych użytkownik wybiera **datę przygotowania przed sezonem**; kolejne wykonanie planowane jest w kolejnych latach na ten sam miesiąc i dzień. Zakres sezonów nie jest jeszcze osobną konfiguracją.
- Wykonanie cyklicznej czynności zapisuje osobną pozycję w historii i przesuwa najbliższy termin po dniu wykonania. Zaległe wystąpienia nie generują automatycznej serii wykonanych wpisów. Przesunięcie zakotwiczone do pierwotnego terminu, także dla końca miesiąca i 29 lutego.
- Kalendarz miesięczny poniedziałek–niedziela z liczbą czynności w dniu; wybór dnia pokazuje zadania i pozwala od razu dodać nowe. Niedokończone zaległości sygnalizowane na panelu/listach.
- Migracja SQLite 2→3 przez dodanie kolumn i tabeli historii, bez kasowania istniejących czynności, spiżarni ani remanentów. Obsługiwany również 1→2→3.
- JSON backup v3 zawiera harmonogramy i historię wykonań; import rozumie również starsze kopie v2 z 0.1.4 (stare zadania trafiają jako jednorazowe bez daty).

## Szybki test Android

1. Przed instalacją wyeksportuj kopię danych na poprzedniej wersji. Wybierz nowe APK podpisane **tym samym** certyfikatem.
2. Sprawdź, że spiżarnia, zadania i aktywny remanent nadal istnieją po instalacji.
3. Dodaj „Zebrać winogrona” z terminem i regułą „Co rok”. Zobacz ją w kalendarzu.
4. Zakończ czynność: pozostaje na liście z kolejnym terminem; otwórz „Historia wykonań”.
5. Dodaj zadanie jednorazowe i zakończ je. Zmień status z powrotem; poprzednie wykonanie zostaje w historii.
6. Sprawdź regułę „Co N dni” (np. 7), datę na przełomie miesiąca i regułę „Przed zimą”.
7. Eksportuj kopię v3, zmień dane, przywróć kopię, zweryfikuj terminy i historię.
8. Importuj starszą testową kopię v2; nazwy i ilości powinny pozostać, stare czynności mają brak daty / regułę jednorazową.
9. Uszkodzony plik JSON nie powinien częściowo podmieniać bazy.

## Ograniczenia tej wersji

- Nie ma systemowych powiadomień, uprawnień do alarmów ani synchronizacji.
- Nie ma jeszcze przypisywania czynności do urządzeń/miejsc/domowników i proponowania godzin pod grafik zmianowy. Moduł jest niezależny, aby umożliwić te relacje w następnych etapach.
- Kalendarz pokazuje najbliższy termin zadania, nie wszystkie hipotetyczne daty przyszłych powtórzeń.
- Zdarzenie ukończenia zachowuje historię nawet po ponownym otwarciu jednorazowej czynności.
- Aktualizowanie bez ręcznego APK wymaga nadal prawidłowego źródła aktualizacji HTTPS; sam działający workflow z podpisem tego nie zastępuje.
