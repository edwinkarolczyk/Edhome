# EDHOME — odbiór 5C: wspólny Budżet Android ↔ Desktop

> **Odbiór niezaliczony do czasu testu na dwóch fizycznych urządzeniach.**
> Testuj na osobnej kopii danych finansowych. Przed aktualizacją wyeksportuj
> ZIP z telefonu i zachowaj kopię danych Desktopu; nie testuj na jedynej księdze.

## Wersje do testu

- Android Beta **0.8.0.64 / versionCode 256** — zabezpieczenie podwójnego odliczania nadpłat; [CI #2009](https://github.com/edwinkarolczyk/Edhome/actions/runs/37841667480) w trakcie. Poprzedni #2002 PASS.; **nie testować na jedynej kopii danych**.
- Desktop Beta **0.7.0.113** — [CI #279 PASS](https://github.com/edwinkarolczyk/Edhome/actions/runs/37821546287), instalator Windows opublikowany.
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

## Dodatkowe próby po uruchomieniu pełnej synchronizacji w obie strony

| ID | Działanie | Warunek zaliczenia | Wynik |
|---|---|---|---|
| A13 | Zmień kwotę rachunku na PC i wyślij tylko zmianę do Androida (tryb klienta PC, nie tylko Hub) | Działające `budgetDelta`, nie pełne nadpisanie stanu, odczyt po ACK zawiera zaktualizowane SQL | ☐ |
| A14 | Wyślij ten sam patch PC→Android drugi raz po symulacji utraconego ACK | Ten sam wynik, jeden wpis historii, te same UUID, brak podwójnej alokacji | ☐ |
| A15 | Telefon zmienia rachunek offline, PC próbuje wysłać starszą zmianę na ten sam UUID | HTTP 409, dane telefonu bez zmian, konflikt jawnie zapisany na PC | ☐ |
| A16 | PC wysyła jednocześnie Budżet i niepowiązaną zmianę magazynową | Paczka nie wykonuje się częściowo; każda zmiana wymaga kontrolowanego osobnego przesłania | ☐ |
| A17 | Telefon zapisuje czynność Projektu i stan Magazynu, następnie PC zmienia tylko kwotę rachunku | Zmienia się tylko Budżet; Projekt, Magazyn i inne tabele mają identyczne rekordy oraz historię; brak pełnego restore | ☐ |
| A18 | Ponownie zeskanuj QR tego samego Desktopu, a następnie na kopii testowej QR innego PC | Ten sam PC zachowuje wspólną bazę i lokalne dane; inny PC pyta o konflikt przed zamianą danych, wybór Telefon robi kopię PC, wybór Desktop robi ZIP telefonu | ☐ |
| A19 | Wyślij dwa razy identyczną paczkę z edycją rachunku i jego rekordów SQLite; potem wyślij starszą paczkę z inną kwotą | Identyczny retry nie dubluje historii ani alokacji; różna zmiana nadal otrzymuje 409 i nie nadpisuje nowszych danych | ☐ |
| A20 | Nadpłata września 50 zł: odlicz 30 zł w październiku, a offline spróbuj odliczyć dalsze 30 zł w listopadzie | Sumarycznie nie może przekroczyć 50 zł; druga paczka odrzucona bez zmiany danych, limit liczony również dla grupowych przelewów | ☐ |

## Kryteria zakończenia

1. **PASS — CI:** Android #2002 i Desktop #279 zakończone sukcesem; opublikowano podpisany APK i instalator. **Pozostałe kryteria wymagają testów fizycznych.**
2. Testy A1–A20 na rzeczywistym Androidzie i Windows zakończone bez P0/P1.
3. Porównanie sumy potwierdzonych transakcji, rozdzielonych kwot, nadpłat i sald przed/po synchronizacji — **różnica 0 gr**, pomijając jawnie zatwierdzone korekty.
4. Kopia ZIP i testowy restore zweryfikowane. W razie awarii wstrzymać wydanie Stable, zachować eksport i diagnostykę z obu urządzeń.
5. Potwierdzenie użytkownika. Dopiero wtedy 5C = odebrany i można rozpocząć 6 (ergonomia i kontrola 25/25).

## Znane ograniczenia przed odbiorem

- Android → Desktop Hub: zmiany ustawień Budżetu są wysyłane per UUID; Desktop → Android wciąż pobiera snapshot z kontrolą lokalnych zmian, nie pełne per-UUID pobieranie.
- Edycja wpłat i nadpłat z poziomu PC nie jest dostępna; synchronizowane są istniejące operacje Androida.
- Wersja Android 0.8.0.59 miała nadmierny zakres przywracania danych podczas odbioru budżetowego patcha; do testu A17 wymagane jest minimum **0.8.0.63 / 255** po zielonym CI. Konflikty finansowe mogą wymagać ręcznego rozstrzygnięcia.
- Sukces CI nie zastępuje testów fizycznych; nie oznaczać etapu ani Budżetu 25/25 jako ukończonego przed odbiorem.
