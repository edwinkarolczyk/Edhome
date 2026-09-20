# EDHOME 0.2.6-beta.1 — planowanie czynności, SQLite v5

## Zakres zrealizowany na `beta`

- Formularz czynności: **priorytet** (niski / normalny / wysoki / pilny) oraz **szacowany czas wykonania w minutach** (1–480, domyślnie 30); pola działają także podczas edycji. Czynność pozostaje samodzielna, miejsce jest opcjonalne.
- Karty czynności prezentują priorytet i czas, ale w tej wersji **nie ma jeszcze** automatycznego planowania terminów, przypisania wykonawcy ani grafiku pracy. Daty i dotychczasowe reguły powtarzania działają jak wcześniej.
- SQLite `v4 → v5`: istniejące zadania otrzymują `priority='normal'` i `duration_minutes=30`, bez zmiany ID, terminu, miejsca czy historii. Ścieżki `v1/v2/v3 → v5` zachowują tę samą kolejność migracji.
- Kopia JSON `databaseVersion: 5` zawiera oba pola. Import kopii `v2–v4` odtwarza bezpieczne wartości domyślne; nowe pola są walidowane przed zmianą bazy. Eksport nadal obejmuje spiżarnię, miejsca, remanent i historię.
- Główna [specyfikacja](SPECYFIKACJA_CALOSC.md), [roadmapa](ROADMAP.md) i [README](../README.md) są prowadzone wg rzeczywistego repozytorium `Edhome`, publicznego kanału GitHub Releases i gałęzi `beta`.
- Wersja `0.2.6-beta.1`, `versionCode=16`. Kanał APK i stały certyfikat bez zmian.

## Test odbiorczy na telefonie

1. W obecnej 0.2.5 wyeksportuj kopię JSON poza telefon/aplikację. Nie odinstalowuj aplikacji.
2. Otwórz EDHOME → Aktualizacje, sprawdź wykrycie wersji z `versionCode=16`, pobranie i potwierdzenie instalacji w Androidzie. Brak wykrycia zgłoś jako niezaliczony test OTA, nie obejście polegające na kasowaniu danych.
3. Otwórz dawną czynność: powinna zachować nazwę, termin, powtarzanie, miejsce i historię; priorytet **Normalny**, czas **30 min**.
4. Dodaj samodzielną czynność bez miejsca z priorytetem Pilny i 45 minutami. Zapisz, edytuj, uruchom ponownie aplikację i sprawdź trwałość obu pól.
5. Spróbuj czasu `0`, `481`, pustego i tekstu: formularz ma odmówić zapisu. Zmień priorytet i czas w zadaniu cyklicznym, wykonaj je, sprawdź kolejny termin i historię.
6. Eksportuj nową kopię JSON, przywróć na kopii/testowej instalacji i sprawdź zachowanie planowania, miejsc, spiżarni, remanentów i historii; wgraj również starszą kopię v4. Nigdy nie próbuj destrukcyjnego testu na jedynej kopii danych.

**Automatyczne testy CI** sprawdzają kontrakt migracji `v1/v2/v3/v4→v5`, zgodność tabel eksportu i import starszych pól. Nie są zastępstwem rzeczywistego testu APK na Androidzie.

## Następna wersja

`0.2.7-beta.1`: pojedynczy wykonawca i lokalny grafik zmianowy / wyjątki; proponowanie trzech terminów pozostaje etapem następnym, dopóki nie ma sprawdzonej dostępności.
