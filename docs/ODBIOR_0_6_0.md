# EDHOME — odbiór całej serii 0.6.0 (beta)

Stan 25.09.2026. **Nie zamknięto jeszcze całej 0.6.0 na telefonie.** Kodowy kandydat końcowy: Beta **0.6.0.32**, SQLite v33. Pierwsze Stable dopiero 1.0.0 po testach i osobnym odbiorze Edwina; `main` bez zmian.

## Kandydat końcowy 0.6.0.32 — 25.09.2026

- [x] Kod: osobna bramka CI `tests/check_0_6_release_gate.py` blokuje publikację, jeśli zniknie którykolwiek z krytycznych kontraktów 0.6.
- [x] Kod: automatyczny rebind `NotificationListenerService` po rozłączeniu Androida oraz przy wznowieniu EDHOME, jeśli użytkownik nadal ma włączony dostęp.
- [x] Kod: diagnostyka odróżnia zgodę Androida, realne połączenie listenera, ostatnie powiadomienie wybranego banku, nierozpoznany komunikat i zapisany sygnał — bez zapisywania surowej treści bankowej.
- [x] Kod: dwuminutowy test nasłuchu rozróżnia „Android nic nie przekazał” od „odebrano, ale parser nie rozpoznał” i od „odebrano + rozpoznano”.
- [x] Kod: powiadomienie bankowe tworzy lokalny szkic i pyta wspólny/prywatny; saldo zmienia się dopiero po późniejszym potwierdzeniu.
- [x] Kod: zabezpieczenie przed ponownym utworzeniem z tego samego powiadomienia oraz ostrzeżenie, gdy podobna operacja została już uzgodniona z wyciągiem w terminie ±2 dni.
- [x] Kod: CSV, tekstowy eksport mBanku, XLSX z tekstowymi wierszami oraz **tekstowy PDF VeloBanku** są odczytywane lokalnie; PDF skanowany jako sam obraz pozostaje świadomie odrzucony (brak OCR w 0.6).
- [x] Kod: diagnostyka importu zapisuje wyłącznie etap, liczbę plików/pozycji i czas; nie zapisuje kwot, opisów, numerów rachunków ani treści dokumentu.
- [x] Kod: Magazyn zachowuje przewinięcie, rozwija gałęzie miejscowo, miniatury są w kopii, a QR ma Drukuj przez Androida / Zapisz PDF / Udostępnij PDF.
- [x] Kod: lista „Dodaj kafelek” i edycja blokują duplikat modułu również wtedy, gdy jego kafelek jest ukryty.
- [ ] Telefon: PayCheck → „Testuj nasłuch banku przez 2 minuty” → prawdziwe powiadomienie Velo/mBank. Wynik musi być ODEBRANO + ROZPOZNANO albo dokładnie wskazać, gdzie blokuje Android/parser.
- [ ] Telefon: pojedyncza etykieta QR — druk/PDF, skan do właściwego obiektu i ten sam QR po przeniesieniu.
- [ ] Telefon: import własnego mBank CSV/XLSX i tekstowego PDF VeloBanku na kopii testowej; sprawdzić „Diagnostyka importu” i ponowiony import bez drugiego księgowania.
- [ ] Telefon: końcowy backup → aktualizacja → restart → restore kopii testowej oraz kontrola PayCheck/Pojazdów/Magazynu/miniatur.
- [ ] Edwin: jawny odbiór całej 0.6.0. Dopiero po nim wolno rozpocząć 0.7.0; `main` nadal bez zmian.

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

## Korekta Magazynu, przewijania i banków — 24.09.2026

- Edwin doprecyzował, że chodzi o **podgląd hierarchii**, nie obowiązkowy akordeon. Magazyn wyświetla miejsca → podmiejsca → pudełka → rzeczy; nagłówki gałęzi można opcjonalnie tapnąć. Zwijanie/rozwijanie zmienia tylko widoczność istniejących podwidoków **bez `render()`, bez cofania przewinięcia i bez resetowania reszty ekranu**. Stan gałęzi pamiętany per trwałe ID.
- EDHOME przechowuje pozycję przewinięcia osobno dla ekranów i przy przebudowie tego samego widoku przywraca ją po ułożeniu elementów, zamiast przechodzić na początek. Odbiór: przewiń długą listę pojazdów, Magazynu i PayCheck, wykonaj akcję, zamknij dialog i sprawdź, że zostajesz w pobliżu ostatniego miejsca.
- Własne zdjęcia rzeczy/pudełek: użytkownik wybiera plik lokalnie; EDHOME zapamiętuje skompresowaną miniaturę JPEG (maks. 32 KB), nie zewnętrzny URI oryginału. Można ją zmienić/usunąć. Miniaturki włączone do zwykłej kopii JSON i walidowane przed przywróceniem (przypisanie do istniejącego ID); brak wysyłania zdjęć do repo ani sieci. Odbiór fizyczny: własne zdjęcie → restart → kopia/odtworzenie → zdjęcie pozostaje.
- Pusta lista banków jest zgłoszonym problemem telefonu. Selekcja korzysta z własnych pól zaznaczenia i przeglądu aplikacji, ma alternatywę **systemowego wyboru aplikacji Androida** oraz ręczne wpisanie identyfikatora pakietu (nie danych logowania!). Odbiór: wybrać bank z listy, wyłączyć/włączyć nasłuch, sprawdzić co najmniej jedną podpowiedź lub zgłosić brak rozpoznania. `main` bez zmian.

## Stałe zasady UI i filtr dodawania kafelków — uwagi Edwina 24.09.2026

- [ ] **„Dodaj kafelek” filtruje już przypisane cele.** Na liście mogą być tylko moduły, które nie mają kafelka, również gdy istniejący kafelek jest ukryty. Przy braku wolnych modułów aplikacja informuje o tym i odsyła do „Przywróć ukryte kafelki”. Powtórny wybór lub edycja istniejącego kafelka nie może przypisać tego samego celu do dwóch kafelków. Istniejących duplikatów z poprzednich wersji nie usuwać bez decyzji użytkownika.
- [ ] **Bez skoków na górę.** Akcje w długich ekranach (Magazyn, Pojazdy, PayCheck, Spiżarnia, kafelki) nie mogą bez potrzeby budować widoku od zera i zerować przewinięcia. Zwijanie i rozwijanie ma być natychmiastowe w bieżącym widoku, przy zachowaniu rozsądnego miejsca wokół dotkniętej pozycji, także po zmianie wysokości gałęzi. Przy przejściu między ekranami zapamiętać osobne pozycje; przed każdym nowym ekranem nie przywracać pozycji z innego.
- [ ] **Magazyn — podgląd bez przymusu drzewka.** Drzewko jest opcjonalnym sposobem nawigacji i nie zastępuje czytelnego podglądu zawartości miejsca/pudełka. Edycja/QR/przenoszenie pozostają dostępne bez rozwijania długich gałęzi.
- [ ] **Własne miniaturki.** Rzeczy oraz pudełka mogą mieć lokalnie wybrane zdjęcie użytkownika. Zdjęcie powinno mieć miniaturę, podgląd i możliwość zmiany/usunięcia; zachować je po aktualizacji i w kompletnej kopii/odtworzeniu z walidacją. Nie kopiować prywatnych zdjęć do publicznego repo ani logów.
- [ ] **Banki — pusta lista wyboru.** Jeżeli nakładka Androida nie zwraca listy aplikacji bankowych, pokazać rzeczywistą listę/filtrowanie albo czytelny sposób wskazania aplikacji; nie udawać, że nasłuch działa, dopóki nie ma wyboru i zgody Androida.

Powyższe zasady traktować jako regresje całej 0.6.0 oraz wymagania dla kolejnych modułów, niezależnie od numeru kolejnego inkrementu. `main` bez zmian; import wyciągów zostaje na końcową stabilizację.
