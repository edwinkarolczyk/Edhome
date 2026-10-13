# EDHOME Desktop Beta 0.7.0.117 — odbiór PayCheck/Budżet

Data: 2026-10-13; gałąź beta, Stable main bez zmian.

## Zrobione
- Po imporcie PDF/CSV/XLSX przelewów pokazuj propozycje 1:1 dla wspólnego Budżetu Android–Desktop. Dodatkowy przycisk w PayCheck pozwala ponownie otworzyć propozycje.
- Kolor zielony tylko gdy nazwa/kwota/termin są jednoznaczne; kolor żółty dla kandydatów z różnicą kwoty lub niejednoznacznym odbiorcą. Przykład plan 4500 zł, bank 4300 zł — kwota bankowa pozostaje 4300 zł.
- Szukanie w otwartych dowodach, bez obcinania do 40/300; wyniki po 60 wierszy na stronę; obliczanie poza wątkiem UI.
- Wyłącznie podgląd, bez automatycznego dopasowania, księgowania, zmiany salda i bez modyfikowania przelewów.
- Kod: 94d7520, 5f43fc9; testy JUnit: 2b5a21b; wersja: 720629f.

## Weryfikacja
- CI Windows: https://github.com/edwinkarolczyk/Edhome/actions/runs/38409002179 — completed/success (kompilacja, JUnit, smoke EXE, publikacja).
- Instalator: https://github.com/edwinkarolczyk/Edhome/releases/download/desktop-beta-latest/EDHOME-Desktop-Beta-Setup.exe
- ZIP: https://github.com/edwinkarolczyk/Edhome/releases/download/desktop-beta-latest/EDHOME-Desktop-Beta-Windows.zip
- Test fizyczny Desktop ↔ Android i import na realnych danych: otwarte.

## Kolejne P0
1. Naprawić obecną opcję importu Desktop tworzącą samodzielne pending z historii bankowej. Według ustaleń nowy plan ma powstawać w Budżecie, a historia bankowa służy do potwierdzania, nie tworzenia przelewu. Zamiast tego dodać bezpieczny wybór istniejącego planu lub jawne „Dodaj do Budżetu” z możliwością cykliczności.
2. Spójne ręczne zatwierdzenie powiązania bank–Budżet i PayCheck, jednokrotne rozliczenie rzeczywistej kwoty, historia, recovery/undo.
3. Sugerowanie i obsługa grup 200+300=500 w obu kierunkach, pulsowanie, fizyczny test, konflikty, backup i synchronizacja LAN.

Nie ogłaszać pełnej zgodności Desktop z Android 0.8.0.108 bez realizacji i testów tych P0.
