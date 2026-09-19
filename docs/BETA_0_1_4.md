# EDHOME 0.1.4-beta.1 — kopia danych przy ręcznych instalacjach

Stan: kod na gałęzi `beta`; NIE oznacza, że gotowy do instalacji APK został opublikowany.
`versionCode=5`, `versionName=0.1.4-beta.1` w wariancie beta.

## Co dodano

- Panel główny / Ustawienia / Aktualizacje → **Kopia danych / przenoszenie**.
- Eksport przenośnego pliku JSON przez systemowy wybór lokalizacji Androida (Storage Access Framework).
- Dane: czynności (także wykonane), spiżarnia, otwarte/zakończone/anulowane sesje remanentu, odpowiedzi i korekty oraz nazwa gospodarstwa i motyw.
- Plik zawiera `formatVersion=1` i `databaseVersion=2`, dzięki czemu przyszłe migracje mogą odrzucać nieobsługiwane kopie, zamiast nadpisywać bazę.
- Import z walidacją rozmiaru (maksymalnie 8 MiB), formatu, kolumn, typów i ID, potem transakcyjne zastąpienie tabel. Najpierw pojawia się wyraźne potwierdzenie.
- PIN nowej instalacji, adres manifestu aktualizacji i diagnostyka **nie są eksportowane ani nadpisywane**. Sam plik JSON jest **nieszyfrowany**: zachowaj go w bezpiecznym miejscu.

## Instrukcja testowa

1. W nowej, testowej instalacji dodaj czynność, oznacz jedną jako wykonaną, dodaj produkt i rozpocznij remanent.
2. Panel → **Kopia danych / przenoszenie** → **Eksportuj kopię danych**. Zapisz plik np. w Dokumentach. Sprawdź, że plik istnieje i nie ma 0 bajtów.
3. Zmień ilość produktu lub dopisz czynność; przy imporcie bieżące dane będą zastąpione, NIE scalone.
4. **Przywróć kopię** → wskaż wcześniej utworzony JSON → sprawdź komunikat ostrzegawczy → potwierdź.
5. Odblokuj aplikację dotychczasowym PIN-em. Zweryfikuj czynności, spiżarnię i postęp remanentu; nowo dodana po eksporcie czynność powinna zniknąć.
6. Wskaż losowy lub uszkodzony plik .json — import powinien odmówić i zostawić dane bez zmian.

## WAŻNE: podpis / istniejąca instalacja 0.1.2

To rozwiązanie **nie omija podpisu pakietu Androida**. Zwykły upgrade wymaga tego samego `applicationId`, certyfikatu i rosnącego `versionCode`.
Obecna 0.1.2-beta.1 nie ma funkcji eksportu danych. Jeśli na telefonie są ważne wpisy, **nie odinstalowuj** jej tylko po to, żeby wypróbować 0.1.4. Trzeba najpierw uzyskać zweryfikowany backup starych danych, np. w oddzielnej, ręcznej procedurze ADB, o ile bieżąca instalacja pozwala na odczyt. Eksport diagnostyki nie jest backupem.

Trwałe podpisywanie zostało odłożone przez właściciela. Workflow nie publikuje nowych instalowalnych APK bez czterech sekretów podpisu; kontynuacja prac nad źródłami nie oznacza działającej ścieżki aktualizacji na telefonie.
[Problem podpisu i konfiguracja](AKTUALIZACJE_PODPIS_I_ODZYSKIWANIE.md).
