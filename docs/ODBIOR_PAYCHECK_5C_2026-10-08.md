# EDHOME — odbiór 5C: wspólny Budżet Android ↔ Desktop

> **Odbiór niezaliczony do czasu testu na dwóch fizycznych urządzeniach.**
> Testuj na osobnej kopii danych finansowych. Przed aktualizacją wyeksportuj
> ZIP z telefonu i zachowaj kopię danych Desktopu; nie testuj na jedynej księdze.

## Wersje do testu

- Android Beta **0.8.0.58 / versionCode 250** (CI #1969 po ostatniej poprawce bootstrapu).
- Desktop Beta **0.7.0.110** (CI #265 po ostatniej poprawce bootstrapu).
- GitHub `beta`; `main`/Stable niezmieniony.

## Scenariusze odbioru

| ID | Działanie | Warunek zaliczenia | Wynik |
|---|---|---|---|
| A1 | Start z pustą testową bazą, parowanie QR, sync i restart obu urządzeń | Ta sama liczba pozycji/odbiorców, kwoty identyczne; bez duplikatów | ☐ |
| A2 | Telefon online: zmień nazwę i kwotę października w A, synchronizuj | PC otrzymuje tę samą pozycję, właściwy miesiąc i event historii | ☐ |
| A3 | PC online: zmień nazwę i kwotę listopada w B, synchronizuj | Android widzi zmianę oraz zachowuje październik i starą historię | ☐ |
| A4 | Rozłącz LAN. Android zmienia A, PC zmienia B. Połącz | Obie niezależne zmiany pozostają; nie pojawia się druga księga | ☐ |
| A5 | Offline zmień **tę samą** kwotę A inaczej na obu urządzeniach | Jawny konflikt, brak cichego „last write wins”; dane do rozstrzygnięcia zachowane | ☐ |
| A6 | Przy jednej potwierdzonej transakcji 100 zł przypisz offline 60 zł do A i 60 zł do B | Druga zmiana odrzucona; zsumowane 120 zł nie jest księgowane | ☐ |
| A7 | Przelew grupowy 100 zł: alokacja 40 zł i nadpłata 60 zł; offline inny przydział 20 zł | Konflikt z uwzględnieniem nadpłaty; brak rozliczenia 120 zł | ☐ |
| A8 | Wyłącz Wi-Fi w czasie synchronizacji, ponów ją | Brak duplikatu płatności, UUID historii pozostają te same | ☐ |
| A9 | Na telefonie edytuj podczas trwającego pobierania dużego snapshotu | Zmiana lokalna zachowana albo jawna blokada konfliktu, nigdy ciche nadpisanie | ☐ |
| A10 | Test zapisu wielu zdarzeń historii (również cache >6000) i jej archiwum | Starsze zdarzenia pozostają po sync/restore; test pomocniczy JUnit i regresji | ☐ |
| A11 | Eksport ZIP → odtworzenie na testowej bazie → ponowna synchronizacja | Te same UUID, liczby pozycji, historię, zaległości, wpłaty i nadpłaty | ☐ |
| A12 | Ręczny konflikt „Desktop wygrywa” | Kopia przed konfliktem zapisana; historia nie ulega bezpowrotnej utracie | ☐ |

## Kryteria zakończenia

1. Zielony CI Android i Desktop dla **bieżącego kodu** oraz poprawne podpisy/instalatory.
2. Testy A1–A12 na rzeczywistym Androidzie i Windows zakończone bez P0/P1.
3. Porównanie sumy potwierdzonych transakcji, rozdzielonych kwot, nadpłat i sald przed/po synchronizacji — **różnica 0 gr**, pomijając jawnie zatwierdzone korekty.
4. Kopia ZIP i testowy restore zweryfikowane. W razie awarii wstrzymać wydanie Stable, zachować eksport i diagnostykę z obu urządzeń.
5. Potwierdzenie użytkownika. Dopiero wtedy 5C = odebrany i można rozpocząć 6 (ergonomia i kontrola 25/25).

## Znane ograniczenia przed odbiorem

- Android → Desktop Hub: zmiany ustawień Budżetu są wysyłane per UUID; Desktop → Android wciąż pobiera snapshot z kontrolą lokalnych zmian, nie pełne per-UUID pobieranie.
- Edycja wpłat i nadpłat z poziomu PC nie jest dostępna; synchronizowane są istniejące operacje Androida. Konflikty finansowe mogą wymagać ręcznego rozstrzygnięcia.
- Sukces CI nie zastępuje testów fizycznych; nie oznaczać etapu ani Budżetu 25/25 jako ukończonego przed odbiorem.
