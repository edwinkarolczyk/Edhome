# EDHOME 0.6.0 — Pojazdy

## 0.6.0-beta.1 — pierwszy działający inkrement

- Karty pojazdów: nazwa, rejestracja, aktualny przebieg, terminy OC i przeglądu oraz notatka. Własny kafelek „Pojazdy” w edytowalnym panelu, bez limitu dziewięciu skrótów.
- Terminy OC i przeglądu są **czytane bezpośrednio z kart pojazdów** przez istniejący kalendarz (dzień, tydzień, miesiąc, agenda); nie tworzą drugiej czynności ani transakcji finansowej. Widok pojazdu pokazuje „za ile dni / po terminie”.
- Historia wykonanych prac: serwis, opony i inne; data, opcjonalny przebieg, opis. Wpis zawiera trwały identyfikator operacji, jego ponowne dostarczenie jest ignorowane. Aktualizacja przebiegu pojazdu i zapis historii odbywają się w jednej transakcji; cofnięcie licznika jest odrzucane. To **historia prac**, nie pełna ewidencja kompletów opon.
- SQLite **23 → 24**: nowe `vehicles`, `vehicle_events`; import kopii ze starszych wersji tworzy obie puste tabele, nie zmieniając dotychczasowych danych. Kopia v24 obejmuje pojazdy i wpisy serwisu; import weryfikuje daty, typy, referencje i duplikaty `operation_id`.
- Nie ma fikcyjnych danych pojazdów, automatycznego przedłużenia OC ani dopisywania kosztów do PayCheck. Ten etap nie obsługuje jeszcze numeru polisy, załączników, historii polis, osobnych kompletów opon, kosztów czy wiązania z celem PayCheck.
- Testy: walidacja Java, migracja SQLite v23→v24 z zachowaniem danych i regresja schematów historycznych, kontrakt backupu i kalendarza, podpisany APK przez CI; test aktualizacji na telefonie jest osobnym odbiorem.
- Stable `main` bez zmian; nie scalać bez każdorazowej wyraźnej akceptacji Edwina.

## 0.6.0-beta.2 — ewidencja kompletów opon (weryfikacja CI i urządzenia)

- Każdy pojazd ma własne komplety letnie, zimowe lub całoroczne: nazwa, DOT,
  bieżnik z dokładnością do 0,1 mm i opcjonalne rzeczywiste Miejsce w EDHOME.
- Ekran pojazdu pokazuje komplet zamontowany oraz przechowywane komplety.
  Można dopisać i edytować komplet, zamontować inny albo zdjąć obecny.
- Jedno potwierdzenie wymiany zmienia oba stany, odkłada zdjęty komplet
  do wskazanego Miejsca, aktualizuje przebieg (jeśli podano) oraz zapisuje
  historię pojazdu w jednej transakcji, z UUID zapobiegającym dublowaniu.
- SQLite v24→v25: kompletów nie dopisuje się fikcyjnie podczas migracji.
  Backup v25 obejmuje komplety i sprawdza identyfikatory pojazdów, miejsca,
  DOT, bieżnik i zakaz dwóch zamontowanych kompletów w jednym pojeździe.
- Moduł nie księguje kosztów do PayCheck i nie usuwa danych przy aktualizacji;
  przed odbiorem konieczny test na prawdziwym telefonie oraz import kopii v24.
- To nadal **seria 0.6.0**. Nie przechodzić do 0.6.1 przed ukończeniem
  i odbiorem całej 0.6.0; `main` bez zmian.

## 0.6.0-beta.3 — czytelność formularzy (odbiór Edwina: TAK)

- Jasne natywne dialogi korzystają z oddzielnej palety o wysokim kontraście; ciemny ekran EDHOME nie narzuca na nie jasnej czcionki ani bladego hinta.
- Jedna wspólna metoda styluje pola, opisy i podkreślenia, a oddzielny adapter styluje zarówno wybraną wartość Spinnera, jak i listę rozwijaną.
- Objęte testowanymi widokami: edycja pojazdu, historia serwisu, komplety i wymiana opon, tworzenie/przenoszenie rzeczy, edycja produktów i potwierdzenie produktu ze skanu; także formularz ceny zakupu.
- Odbiór: otworzyć w każdym motywie i przy wysuniętej klawiaturze; sprawdzić puste i wypełnione pola oraz listę rozwijaną. Tylko UI: wersja danych i zasady księgowania bez zmian; nie wprowadzać zmian na `main`.

## 0.6.0-beta.4 — polisy OC i historia odnowień

- Na karcie pojazdu można zapisać polisę OC: ubezpieczyciel, numer, daty ważności i opcjonalna notatka.
- Nowy rekord zachowuje poprzednią polisę w historii. Opcja „bieżąca” aktualizuje istniejący termin OC pojazdu, więc ten sam termin widać w kalendarzu bez drugiej czynności. Archiwalny zapis nie zmienia terminu.
- Jedna polisa bieżąca na pojazd; zapis i aktualizacja terminu są jedną transakcją. Ponowienie operacji nie tworzy drugiego rekordu ani nie przywraca starszej polisy.
- SQLite v25→v26 i backup v26 obejmują historię polis. Przy imporcie starszej kopii historia polis pozostaje pusta — bez dopisywania fikcyjnych dokumentów.
- **Nie ma jeszcze** plikowych załączników ani integracji z celami PayCheck. Nie księgować automatycznie wydatków; `main` nietknięta.
- Odbiór: dodać polisę bieżącą i archiwalną; sprawdzić kalendarz, historię oraz zachowanie danych po aktualizacji.

## 0.6.0-beta.5 — wybór dat OC z kalendarza i opcjonalny koniec polisy

- Początek i koniec polisy OC wybiera się z natywnego kalendarza Androida, bez ręcznego wpisywania RRRR-MM-DD.
- Domyślnie wyliczany koniec to początek + jeden rok kalendarzowy − jeden dzień (np. 2026-09-09 → 2027-09-08), z obsługą lat przestępnych.
- Podpowiedź jest opcjonalna: odznaczenie automatycznego końca albo wybranie daty końca w kalendarzu pozwala na indywidualny okres. Ponowny wybór automatu wylicza koniec na nowo. Zmiana początku po ręcznym nadpisaniu nie kasuje indywidualnego końca.
- Walidacja dat i historii polis pozostaje po stronie zapisującej; ta zmiana nie księguje nic do PayCheck i nie modyfikuje schematu SQLite v26 ani kopii danych.
- Test odbioru: zaznacz datę początku; sprawdź proponowany koniec; wskaż inny koniec; zmień początek i sprawdź, czy indywidualny koniec pozostał; przywróć automat. `main` bez zmian.

## 0.6.0.1 — daty pojazdów z kalendarza (odbiór na telefonie wymagany)

- W edycji pojazdu OC i przegląd, a w historii serwisu i wymianie kół daty wybiera się z kalendarza Androida. Nie trzeba ręcznie wpisywać RRRR-MM-DD.
- Opcjonalne terminy OC i przeglądu można jawnie wyczyścić. Edytując istniejący termin, kalendarz otwiera zapisaną datę. Serwis i wymiana kół domyślnie podpowiadają bieżącą datę.
- Wersjonowanie testowego kanału zmienione na `0.6.0.1`, `0.6.0.2` itd., bez sufiksu `-beta.N` w numerze aplikacji. Kanał instalacyjny Beta, pakiet i podpis pozostają bez zmian.
- Brak zmian schematu SQLite v26, kopii i księgowania PayCheck. Stable `main` bez zmian.

## 0.6.0.2 — stabilizacja referencji miejsca dla opon

- Usunięcie Miejsca jest zablokowane, gdy jest przypisane choćby do jednego kompletu opon pojazdu; dotychczasowy warunek sprawdzał tylko podmiejsca oraz rzeczy/pudełka magazynu.
- Warunek jest sprawdzany **przed** odpięciem czynności i zakupów, więc odrzucona próba nie zmienia niczego w pozostałych modułach. Komplet, jego nazwa, DOT, bieżnik, lokalizacja i historia nie są usuwane ani przenoszone.
- Po ręcznym przeniesieniu kompletu do innego Miejsca (lub usunięciu przypisania) można usunąć poprzednie Miejsce na dotychczasowych zasadach.
- Regresja SQLite + kontrakt UI obejmują zajęte miejsce, zachowanie czynności i możliwość usunięcia dopiero po zwolnieniu opon. Schemat SQLite v26 i kopia danych bez zmian.
- To inkrement stabilizacyjny; nie oznacza zakończenia całego 0.6.0 ani zgody na `main`.

## 0.6.0.3 — odkrywalność terminów pojazdów w kalendarzu

- Odbiór Edwina dla 0.6.0.2 jest częściowy: dane Audi, OC, przeglądu i opon są zachowane, ale w bieżącym widoku kalendarza brakowało widocznych terminów.
- Kalendarz dotąd pokazywał terminy OC/przeglądu w wybranym miesiącu/dniu, a Agenda obejmowała tylko 30 dni — termin w 2027 roku nie był widoczny przy otwarciu kalendarza we wrześniu 2026.
- Nowa lista przyszłych OC i przeglądów jest widoczna niezależnie od aktualnego miesiąca; dotknięcie terminu otwiera właściwy dzień i miesiąc. Na karcie pojazdu dodane są przejścia „Pokaż OC w kalendarzu” i „Pokaż przegląd w kalendarzu”. Dodano wybór dowolnej daty.
- Terminy nadal są czytane bezpośrednio z tabeli pojazdów — żadnych drugich zadań, nowych transakcji ani zmian w bazie SQLite v26.
- Odbiór: przy dzisiejszym miesiącu powinny być widoczne terminy 2027 na liście; przejście do nich ma pokazać wydarzenie w wybranym dniu. Sprawdzić też zwykłe czynności i agendę 30-dniową. Stable `main` bez zmian.

## 0.6.0.4 — polisa OC ↔ istniejący wspólny cel PayCheck

- Podczas dodawania polisy OC można wybrać opcjonalny **istniejący wspólny cel PayCheck**. Brak wyboru nie tworzy nowego celu; polityka prywatnych finansów w Becie bez PIN-u pozostaje bez zmian.
- Karta pojazdu pokazuje powiązany cel i jego aktualny postęp (odłożone/kwota docelowa), a widok wspólnych celów pokazuje pojazdy i termin OC powiązanych **bieżących** polis, bez duplikatów zdarzeń kalendarzowych.
- Link i nowa polisa zapisywane są atomowo; sprawdzamy istnienie celu przed zapisem. Ponowienie tego samego operation_id nie dodaje polisy, celu ani wpłaty. Historia polis i jej poprzednie powiązania nie są nadpisywane.
- SQLite v26→v27 dodaje opcjonalny `goal_id` do polisy; stare polisy zachowują dane i mają pusty link. Kopia v27 przechowuje link i odrzuca referencje do nieistniejących celów; import kopii v26 ustawia brak linku.
- Link jest wyłącznie informacyjny: nie tworzy wpłat, wydatków, nowych sald ani prywatnych danych; Stable `main` bez zmian.
- Odbiór: utworzyć w PayCheck wspólny cel „OC”; dodać bieżącą polisę z linkiem; sprawdzić kartę pojazdu, cel i termin kalendarza; dodać polisę bez linku; zrestartować i sprawdzić dane i kopię.

## 0.6.0.5 — niezależne przypomnienia OC/przeglądu

- Na karcie pojazdu nowy formularz „Przypomnienia OC / przeglądu”. Osobno dla każdego terminu ustawiasz: wyłączone (domyślnie), 30, 14, 7, 1 dzień wcześniej albo w dniu terminu; powiadomienie około 09:00 z uwzględnieniem godzin ciszy.
- Powiadomienia prowadzą do modułu Pojazdy, nie tworzą czynności, wpłat ani wydatków PayCheck. Powiadomienia systemowe na Androidzie 13+ wymagają zgody. Android może opóźnić dostarczenie alarmu.
- Po zmianie daty, odnowieniu OC, restarcie telefonu lub aktualizacji APK następuje ponowne planowanie. Alarm sprawdza aktualny termin i ustawienie, żeby nie wysłać powiadomienia o starej polisie. Potwierdzenie wysłania zapobiega duplikatom po wznowieniu/restartach.
- SQLite v27→v28: dwa opcjonalne pola przypomnień w tabeli pojazdów. Starsze rekordy mają przypomnienia domyślnie wyłączone; eksport/import obejmuje ustawienia, waliduje wartości i nie usuwa danych. Stable `main` bez zmian.
- Test na telefonie: ustaw termin niedługo przed datą testu, włącz OC, sprawdź prośbę o zgodę systemową, godziny ciszy, brak duplikatu, aktualizację starego OC i niezależny przegląd. Fizyczny odbiór osobno.

## 0.6.0.6 — koszty pojazdu i opcjonalna płatność we wspólnym PayCheck

- Na karcie pojazdu „Zapisz koszt pojazdu”: data, OC/przegląd/serwis/opony/inne, kwota PLN, notatka i domyślnie **wyłączona** opcja „Zapisz również jako WYDATEK we wspólnym PayCheck”.
- Bez zaznaczenia powstaje tylko wpis historii kosztów. Z zaznaczeniem wpis pojazdu i **jedna** transakcja wspólna PayCheck zapisują się atomowo na tym samym `operation_id`, bez księgowania kolejnej wpłaty lub przekazu do celu oszczędnościowego. Sumy kosztów widoczne na karcie pojazdu.
- Powtórzenie operacji nie tworzy duplikatu; błąd części transakcji cofa cały zapis. Nie dotykamy prywatnego sejfu.
- SQLite v28→v29, eksport/import v29 zachowuje nowe koszty i link oraz sprawdza zgodność kwot, kategorii i referencji. Import v26–v28 zachowuje dane i dodaje pustą historię kosztów.
- To nadal inkrement do odbioru fizycznego; brak załączników dokumentów i zakończenia całej 0.6.0. Stable `main` bez zmian.

## 0.6.0.7 — pierwsza korekta PayCheck: „Do potwierdzenia”

- NOWE wpisy wspólne, również koszt pojazdu z zaznaczoną integracją, są zapisywane jako `pending`. Nie wpływają na saldo potwierdzone. Brak zaznaczenia integracji nadal zostawia koszt tylko przy pojeździe.
- Ekran wspólnego PayCheck rozróżnia wpisy `Do potwierdzenia` i `Potwierdzone`. Użytkownik może RĘCZNIE potwierdzić dopiero po sprawdzeniu transakcji w banku lub na wyciągu. Potwierdzenie zmienia saldo tylko raz; nie oznacza automatycznego sprawdzenia banku.
- Dotychczasowe wpisy pozostają potwierdzone w celu zachowania salda po aktualizacji; nie przypisujemy im fikcyjnej historii weryfikacji bankowej.
- SQLite v29→v30: status z domyślną wartością `confirmed` dla historycznych transakcji; import kopii starszych wersji również zachowuje stare księgowania jako potwierdzone. Nowe kopie przenoszą status `pending/confirmed`.
- Nie ma jeszcze automatycznego odczytu powiadomień bankowych, importu wyciągów, deduplikacji między źródłami ani ostatecznego odbioru 0.6.0. Stable `main` bez zmian.

## Kolejne inkrementy 0.6 — zakres, nie wdrożenie

1. Doprecyzowanie ewidencji opon po testach beta.2; ocena powiązań z innymi rzeczami w magazynie bez kopiowania stanu.
2. Polisy i dokumenty z historią, odnowienia OC, przypięcie terminu do celu PayCheck bez drugiego księgowania oraz czytelne powiadomienia.
3. Dom, remonty i instalacje jako następne moduły wykorzystujące wspólne miejsca, czynności, kalendarz i finanse.

Źródło zakresu: [ROADMAP.md](ROADMAP.md) i [SPECYFIKACJA_CALOSC.md](SPECYFIKACJA_CALOSC.md).

## 0.6.0.8 — prawdziwe pochodzenie potwierdzenia

- Migracja SQLite v30→v31 zapisuje źródło: `none` (nowy wpis oczekujący), `manual` (ręczne potwierdzenie przez użytkownika), `legacy` (stary wpis — brak informacji o banku). Ręczne potwierdzenie zachowuje także czas. Nie jest to automatyczna weryfikacja bankowa.
- Wspólne koszty pojazdu zaczynają jako `pending/none`; nie zmieniają salda przed potwierdzeniem. Ponowienie potwierdzenia nie zmienia źródła ani czasu.
- Kopia v31 obejmuje źródło i czas potwierdzenia; import v29 i v30 działa również po aktualizacji. Kopie prywatnego sejfu nadal pozostają oddzielne.
- W kolejnych inkrementach 0.6: prawdziwa integracja wyciągów i powiadomień bankowych z identyfikacją źródła, dokumenty pojazdów, testy i odbiór. Nie przenosić na `main` bez zatwierdzenia.

## 0.6.0.9 — ręczne uzgadnianie importowanego wyciągu CSV

- W PayCheck przycisk „Uzgodnij z wyciągiem CSV”. Plik wybierany jest przez systemowy picker Androida; aplikacja nie loguje się do banku i **nie uwierzytelnia pochodzenia pliku**.
- Wspierany na tym etapie jest CSV UTF-8 z separatorem średnikowym i kolumnami `Data;Kwota;Id transakcji;Opis`; data `RRRR-MM-DD` lub `DD.MM.RRRR`, kwota ujemna jako wydatek, dodatnia jako wpływ. Do 256 KB i 250 transakcji. Konieczny stabilny identyfikator transakcji z banku; pliki bez identyfikatora są odrzucane, zamiast zgadywania duplikatów.
- Użytkownik wskazuje bank i **ręcznie wybiera powiązanie** wiersza CSV z istniejącą transakcją `pending` o takim samym znaku i kwocie; import nie tworzy nowych wydatków, nie rusza sejfu prywatnego, niczego nie potwierdza bez wyboru. Ten sam `bank+identyfikator` nie może potwierdzić dwóch wpisów nawet po ponownym imporcie.
- Wspólne saldo zmienia się jednorazowo dopiero przy zatwierdzeniu skojarzenia. Zachowujemy skrót identyfikatora i datę w celu deduplikacji; surowe wiersze CSV nie są utrwalane. Na ekranie źródło oznaczone „Uzgodnione z importowanym CSV” — to nie jest gwarancja autentyczności banku.
- SQLite v31→v32; backup v32 przechowuje referencję/dzień i waliduje ich spójność. Starsze kopie do v31 zachowują operacje bez dorabiania fikcyjnych dopasowań.
- **Nie jest to jeszcze obsługa powiadomień Androida, import dowolnego formatu dowolnego banku ani automatyczne rozpoznawanie przelewów własnych.** Pozostaje zakres kolejnych inkrementów 0.6 oraz dokumenty pojazdów i pełna stabilizacja; `main` nietknięta.


## 0.6.0.10 — dokumenty pojazdu

- Karta każdego pojazdu otrzymuje lokalny rejestr dokumentów. Typy: dowód rejestracyjny, polisa/ubezpieczenie, przegląd, faktura/rachunek, dokument serwisowy i inne.
- Dokument przechowuje nazwę, opcjonalny numer, datę dokumentu, opcjonalny termin ważności oraz notatkę. Wpis jest przypisany do jednego istniejącego pojazdu i ma własny identyfikator operacji, więc ponowienie zapisu nie tworzy duplikatu.
- Rejestr dokumentów **nie tworzy wydatku PayCheck, celu ani czynności**. Koszt dokumentu lub polisy trafia do finansów tylko przez istniejący jawny mechanizm kosztu pojazdu.
- SQLite v32→v33 dodaje tabelę `vehicle_documents`; backup v33 zachowuje dokumenty, a starsze kopie importują pusty rejestr bez utraty wcześniejszych danych.
- Ten inkrement przechowuje ewidencję dokumentu, nie binarny skan/PDF. Załączniki plikowe wymagają osobnego modelu kopii danych, aby aktualizacja lub odtworzenie nie zgubiły pliku.
- Stable `main` pozostaje bez zmian.

## Odbiór telefonu 0.6.0.10 — zgłoszenie Edwina 24.09.2026

**6 z 7 scenariuszy zaliczonych na fizycznym urządzeniu:** aktualizacja i kopia danych; zachowanie starego salda; nowy wspólny wydatek oczekujący i ręczne potwierdzenie; koszt pojazdu → wspólny PayCheck; rejestr dokumentu pojazdu po restarcie; zachowanie danych i podstawowa stabilność. **Nieprzetestowane:** import CSV, więc cała 0.6.0 pozostaje otwarta. Te wyniki są deklaracją odbioru użytkownika; nie oznaczają wykonania testu plikowych załączników ani automatycznego potwierdzania bankiem.

### Test 4 — import CSV bez prawdziwych danych bankowych

1. Wyeksportuj kopię. Zanotuj potwierdzone saldo w PayCheck. Utwórz **nowy** wspólny wydatek 12,50 zł, pozostaw „Do potwierdzenia”. Nie używaj wpisu potwierdzonego w teście 3.
2. Skopiuj [syntetyczny CSV](../tests/fixtures/paycheck_statement_acceptance.csv) jako lokalny plik UTF-8 `edhome-test.csv`, separator średnikowy. Kolumny: `Data;Kwota;Id transakcji;Opis`; testowy wiersz `2026-09-24;-12,50;EDHOME-TEST-CSV-1250-001;Test uzgodnienia PayCheck`.
3. Otwórz PayCheck → „Uzgodnij z wyciągiem CSV”; wybierz lokalny plik i wpisz **fikcyjną nazwę banku „EDHOME TEST”**. Wskaż właściwy wpis oczekujący na 12,50 zł i jawnie potwierdź skojarzenie. Saldo ma zmienić się **dokładnie raz** o −12,50 zł; wpis staje się potwierdzony i ma źródło „Uzgodnione z importowanym CSV”. Sam podgląd/import bez zatwierdzenia nie może zmienić salda.
4. Zaimportuj **ten sam CSV z tą samą fikcyjną nazwą banku** ponownie. Identyfikator `EDHOME-TEST-CSV-1250-001` musi zostać odrzucony jako już użyty, bez kolejnej zmiany salda, nawet po restarcie aplikacji.
5. Sprawdź brak wpływu na prywatny sejf, inne wydatki i dokumenty pojazdu. Jeśli test nie przejdzie, zachowaj plik testowy, eksport logów i zanotuj saldo przed/po.

**Uwaga:** „EDHOME TEST” to wyłącznie etykieta w pliku testowym, nie prawdziwy bank; CSV sam w sobie nie dowodzi autentyczności operacji. Dopóki użytkownik nie potwierdzi testu 4, nie oznaczać importu jako odebranego. Stable `main` bez zmian; 1.0.0 dopiero po testach całego wydania i wyraźnym odbiorze.

## 0.6.0.11 — pierwszy krok do wielobankowej skrzynki PayCheck (odbiór telefonu wymagany)

- Obecny wybór CSV pozwala wskazać **do 10 plików jednego wskazanego banku** w jednej partii; ponowione identyfikatory pomiędzy tymi plikami są pomijane. Różne banki na tym etapie dodawaj oddzielnymi partiami; **nie ma jeszcze wspólnej trwałej kolejki wielu źródeł**.
- Parser obsługuje separator średnikowy, tabulator lub przecinek z ujętą w cudzysłów kwotą dziesiętną, oraz część alternatywnych nagłówków. Wymaga stabilnego identyfikatora transakcji i kwoty ze znakiem: brak identyfikatora nie jest powodem do wymyślania nowego wpisu.
- Po odczycie widok ma oznaczać, czy istnieje dokładnie jedna propozycja o zgodnym rodzaju i kwocie, kilka propozycji czy brak wpisu oczekującego. Filtry: wszystkie, jedna propozycja, kilka propozycji, brak pary, wydatki i wpływy. Jednoznaczną propozycję zatwierdzasz bez wybierania z kolejnej listy; żadna propozycja sama nie księguje.
- Identyfikator i klucz bankowy nadal są deduplikowane; saldo liczy wyłącznie potwierdzone wpisy. SQLite pozostaje v33 i dotychczasowe dane są zachowane.
- **To nie jest pełna realizacja nowego wymagania:** brak wspólnej lokalnej skrzynki z różnymi bankami, PDF/obrazów, automatycznego odczytu powiadomień i uwierzytelnionego bankowego potwierdzania. Docelowy model oraz kryteria: [PAYCHECK_MULTI_BANK_0_6_0.md](PAYCHECK_MULTI_BANK_0_6_0.md).
- `main` bez zmian; dopiero zielone CI, podpisana publikacja i test fizycznego telefonu uprawniają do uznania tego inkrementu za wydany i odebrany.

## 0.6.0.12 — pierwsza obsługa eksportu mBanku do ręcznego testu

- Na podstawie zrzutu Edwina wdrożony lokalny parser `BankStatementMbank` dla mBankowego eksportu zawierającego nagłówek `#Data księgowania;#Data operacji;...;#Kwota;#Saldo po operacji`. Pomija część z danymi rachunku, saldo początkowe, końcowe i stopkę.
- Rozpoznaje tekstowy eksport rozdzielany średnikiem (w tym CSV otwarty w arkuszu jako jedna kolumna) oraz **XLSX z wierszami tekstowymi** zawierającymi taki eksport. XLSX rozpakowywany i odczytywany lokalnie, bez wysyłania pliku do sieci. Stary binarny XLS i zaszyfrowany arkusz nie są w tym inkremencie obsługiwane.
- W mBankowym formacie bez stabilnego identyfikatora transakcji odróżnienie operacji opiera się na odcisku rachunku z nagłówka (jeśli istnieje), dacie księgowania i operacji, znaku i kwocie, saldzie po operacji, opisie i tytule. Dwie płatności na tę samą kwotę z **różnym saldem końcowym** pozostają różnymi wpisami. Brak salda po operacji lub nierozróżnialne rekordy skutkują bezpiecznym odrzuceniem importu, a nie domniemaniem unikalnego ID banku.
- Wszystkie dopasowania są jedynie propozycjami; saldo wspólne zmienia się **wyłącznie po zatwierdzeniu użytkownika**. Nie ma dostępu do prawdziwego banku, odczytu PDF VeloBanku, OCR ani odczytu powiadomień systemowych. Wspólny i prywatny PayCheck pozostają rozdzielone. SQLite v33, bez zmiany schematu.
- **Odbiór fizyczny:** Edwin wybiera swój oryginalny plik mBanku na telefonie (bez przesyłania go do repo), oznacza partię jako mBank i sprawdza listę, rozpoznanie kwot/dat, brak duplikatów i zachowanie starego salda po samym imporcie. Jeśli pierwotny plik jest binarnym XLS, należy to odnotować jako niezrealizowany wariant, nie sugerować, że działa. Nie nazywać adaptera gotowym przed zielonym CI i testem urządzenia; `main` nietknięta.

## Status odbioru importu bankowego — 24.09.2026

- Test telefonu: **NIEZALICZONY**. Edwin potwierdził, że import bankowy w bieżącej Becie nie działa poprawnie / nie wczytuje pliku w użyteczny sposób.
- Decyzja: **odłożyć import bankowy na końcową stabilizację 0.6.0**. Nie blokować dalszych prac nad pozostałym zakresem 0.6.0, ale nie oznaczać importu jako gotowego ani odebranego.
- Dotychczasowe testy telefonu pozostają ważne: kopia i aktualizacja, stare saldo, ręczne potwierdzanie PayCheck, koszt pojazdu→PayCheck, dokumenty pojazdu oraz zachowanie danych/stabilność — OK.
- Do ponownego testu na końcu: mBank/CSV/XLSX, VeloBank PDF, wielobankowa kolejka, deduplikacja i brak podwójnego księgowania.
- `main` bez zmian; nie zamykać całej 0.6.0 przed ponownym testem importu.

## 0.6.0.14 — indywidualne zwijanie kart pojazdów

- Każdy pojazd w module Pojazdy ma własny przycisk **„Zwiń pojazd” / „Rozwiń pojazd”**. Po zwinięciu pozostają jego nazwa, rejestracja (jeżeli jest) oraz przycisk rozwijania. Sekcje OC, przeglądów, dokumentów, kosztów, historii i opon są ukryte, nie usuwane.
- Stan jest pamiętany **osobno po stabilnym ID pojazdu** w lokalnych ustawieniach Androida; zmiana nazwy, kolejności listy, przejście na inny ekran i ponowne uruchomienie nie rozwijają go samoczynnie. Nowe pojazdy domyślnie są rozwinięte.
- Zwijanie nie wykonuje zapisu do tabel pojazdów ani nie księguje PayCheck; SQLite pozostaje v33. Pamięć zwinięcia to lokalna preferencja interfejsu, nie zawartość eksportu JSON danych gospodarczych.
- **Odbiór telefonu:** dodaj dwa pojazdy lub użyj istniejących; zwiń pierwszy, sprawdź, że drugi pozostał rozwinięty; uruchom aplikację ponownie i wróć do Pojazdów; rozwiń pierwszy i sprawdź wszystkie poprzednie dane. Import bankowy nadal otwarty i odłożony do końcowej stabilizacji. `main` bez zmian.

## Zmiana zakresu i 0.6.0.15 — tapnięcie nazwy pojazdu i bankowe podpowiedzi

- **Decyzja Edwina:** bez dodatkowego przycisku na karcie pojazdu. Tapnięcie w wiersz nazwy/strzałki zwija lub rozwija dany pojazd, a stan pozostaje zapamiętany per ID. Rejestracja jest widoczna przy zwinięciu; żadnych zmian danych pojazdu.
- **Załączniki PDF/zdjęcia do dokumentów pojazdu odłożone**, nie uważać ich za warunek domknięcia najbliższej części 0.6.0. Rejestr metadanych z 0.6.0.10 pozostaje bez zmian.
- Import plików bankowych **NIEZALICZONY NA TELEFONIE** i odłożony na koniec. Nie zastępować tej informacji zielonym CI.
- W bieżącym inkremencie Beta można wybrać z listy zainstalowanych aplikacji dokładne pakiety bankowe i jawnie otworzyć ustawienia Androida, aby samodzielnie nadać uprawnienie dostępu do powiadomień. Domyślnie funkcja jest wyłączona. Odznaczenie/wyłączenie przestaje przetwarzać wiadomości i czyści lokalną kolejkę.
- Usługa analizuje **tylko powiadomienia wybranych aplikacji** i tylko wtedy, gdy da się jednoznacznie odczytać kierunek oraz jedną kwotę PLN; kodów, logowania i komunikatów niejednoznacznych nie zapisuje. Lokalnie zapisuje tylko skrót klucza powiadomienia, pakiet źródłowy, kierunek, kwotę i czas (maks. 40 wskazań, do 14 dni), nie treść, nazwiska, numer konta ani kod. Skrót pozwala pominąć powtórną dostawę tego samego powiadomienia, **nie stanowi ID transakcji bankowej**. Podpowiedzi nie są objęte zwykłą kopią JSON.
- PayCheck pokazuje liczbę zgodnych wpisów oczekujących po znaku i kwocie. Przy jednej propozycji można ją sprawdzić i ręcznie potwierdzić; przy kilku trzeba wskazać właściwą. **Otrzymanie powiadomienia i znalezienie propozycji NIE zmienia salda, NIE uwierzytelnia banku i nie potwierdza zaksięgowania.** Dopiero ręczne zatwierdzenie przez Edwina uruchamia istniejącą operację `pending → confirmed` jednokrotnie. Brak źródła bankowego z gwarancją finalnego rozliczenia.
- Dostęp na Androidzie jest szeroki z punktu widzenia systemu, mimo filtrowania wewnątrz aplikacji; wybór banku i zgoda w ustawieniach są odrębnymi krokami. Wymaga fizycznego testu na telefonie i w rzeczywistej aplikacji bankowej; nie deklarować skuteczności dla mBank/VeloBank przed tym testem. Stable `main` bez zmian.
