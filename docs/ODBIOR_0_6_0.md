# EDHOME — odbiór całej serii 0.6.0 (beta)

Stan 24.09.2026. **Nie zamknięto całej 0.6.0.** To lista kryteriów, nie automatyczny raport zaliczenia. Wersja referencyjna: podpisana Beta **0.6.0.16**, SQLite v33. Pierwsze Stable dopiero 1.0.0 po testach i osobnym odbiorze Edwina; `main` bez zmian.

## Potwierdzone dotychczas przez Edwina na telefonie

- [x] Aktualizacja z kopią bez kasowania danych (0.6.0.10).
- [x] Stare saldo PayCheck po aktualizacji.
- [x] Oczekujący wydatek bez zmiany salda; ręczne potwierdzenie zmienia saldo jednokrotnie.
- [x] Koszt pojazdu przekazany do wspólnego PayCheck jako oczekujący.
- [x] Rejestr metadanych dokumentów pojazdu zachowany po restarcie.
- [x] Podstawowa stabilność i zachowanie danych.
- [ ] Import bankowy — **użytkownik zgłosił, że NIE DZIAŁA**. Odłożony na ostatni etap tej serii.

## Bieżący odbiór 0.6.0.16 — telefon

- [ ] Wejdź do Pojazdów; naciśnij **wiersz nazwy / strzałkę**, nie osobny przycisk. Zwiń pierwszy pojazd, drugi zostaw otwarty; przejdź na ekran główny, wróć, zamknij i ponownie uruchom aplikację. Układ nie powinien się resetować. Wszystkie dokumenty, OC, przebiegi, opony, koszty i historia nadal istnieją.
- [ ] PayCheck → **Powiadomienia bankowe • wybierz aplikacje**. Przed wyborem lista sygnałów ma być wyłączona. Wybierz jedną prawdziwą aplikację bankową; w systemowych ustawieniach Androida zezwól na dostęp do powiadomień dla EDHOME. Sprawdź komunikat systemu (dostęp jest szeroki, mimo filtrowania po stronie EDHOME).
- [ ] Przy **zwykłym nowym powiadomieniu bankowym** po włączeniu dostępu otwórz PayCheck. Jeżeli wiadomość ma jednoznaczną kwotę PLN i kierunek, powinna powstać podpowiedź z bankiem/kwotą; przy braku kwoty lub niejasnym komunikacie nie obiecywać odczytu. Samo odebranie powiadomienia **nie zmienia salda**.
- [ ] Jeżeli istnieje oczekujący wpis o identycznej kwocie i rodzaju, podpowiedź powinna pozwolić go świadomie sprawdzić i ręcznie potwierdzić; przy kilku wpisach o tej samej kwocie trzeba wskazać właściwy. Saldo zmienia się raz. Nie potwierdzaj niczego tylko na podstawie powiadomienia — sprawdź rzeczywiste zaksięgowanie w aplikacji banku.
- [ ] Wybierz „Odrzuć sygnał”, potem wyłącz i wyczyść nasłuch. Stary sygnał nie powinien być widoczny. Nowe powiadomienie aplikacji spoza listy wyboru nie powinno utworzyć podpowiedzi. Prywatny sejf i pozostałe dane bez zmian.

**Nie trzeba wysyłać pliku wyciągu, zrzutu prawdziwego salda ani pełnej treści bankowego powiadomienia.** Gdy coś nie działa, wystarczy numer wersji, nazwa wybranej aplikacji oraz informacja „powiadomienie przyszło / nie przyszło”, „pojawiła się podpowiedź / nie” i ewentualny diagnostyczny kod zdarzenia bez danych finansowych.

## Końcowa stabilizacja 0.6.0 (jeszcze otwarta)

- [ ] Regresja kopii i odtwarzania, migracji danych, opon/OC/pojazdów i salda PayCheck na testowej kopii, niezależnie od CI.
- [ ] Powiadomienia bankowe — odbiór zgodny z powyższymi punktami; zgodność konkretnego banku zależy od treści jego realnego powiadomienia.
- [ ] **Na końcu**: naprawa importu CSV/XLSX mBank i PDF VeloBank, wiele banków, duplikaty, filtracja i test na prawdziwym telefonie bez wysyłania oryginalnych plików do repo.
- [ ] Podpisana aktualizacja APK, restart, brak utraty danych i ręczna akceptacja Edwina.

### Wyłączone z bieżącego zakresu decyzją użytkownika

Załączanie skanów PDF/zdjęć do dokumentów pojazdu jest odłożone. Rejestr metadanych dokumentów pozostaje w 0.6.0. Nie traktować braku funkcji załączania jako błędu tej serii, dopóki Edwin nie zmieni decyzji.

## Magazyn — drzewko jako warunek odbioru 0.6.0 (zgłoszenie Edwina)

- Dotychczasowy widok „Rzeczy • pudełka • QR” był **płaską listą**, mimo że model przechowywał hierarchię. To było pominięte w planie odbioru. W `0.6.0.17` zaplanowano drzewko **miejsce → podmiejsce → pudełko → rzecz**, również pudełko w pudełku; bez utraty QR, działań przenoszenia i wypożyczenia. Rzeczy i pudełka bez lokalizacji pozostają widoczne w „Bez przypisanego miejsca”.
- [ ] Tapnij nagłówek jednego miejsca: zwija się cała jego gałąź; inne miejsca pozostają w dotychczasowym stanie.
- [ ] Tapnij pudełko: zawartość znika i wraca po rozwinięciu; nie pojawia się drugi raz na końcu jako osobna pozycja.
- [ ] Przejdź do innej zakładki, uruchom EDHOME ponownie; stan poszczególnych miejsc i pudełek jest pamiętany po ich **trwałym ID**, nie po nazwie i kolejności.
- [ ] Przenieś rzecz i pudełko; sprawdź nową ścieżkę w drzewku, bez zmiany QR. Sprawdź pożyczanie/zwrot i zabezpieczenie przed usunięciem pudełka z zawartością.
- [ ] Sprawdź przypadek bez miejsca oraz dane starego magazynu po aktualizacji, bez resetu i bez ingerencji w saldo PayCheck.

**Status tego punktu:** wdrożenie w kodzie `beta` nie jest jeszcze odbiorem na urządzeniu; warunek `0.6.0` pozostaje otwarty do testu fizycznego telefonu. Import bankowy nadal na sam koniec.
