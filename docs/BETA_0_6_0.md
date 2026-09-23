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

## Kolejne inkrementy 0.6 — zakres, nie wdrożenie

1. Doprecyzowanie ewidencji opon po testach beta.2; ocena powiązań z innymi rzeczami w magazynie bez kopiowania stanu.
2. Polisy i dokumenty z historią, odnowienia OC, przypięcie terminu do celu PayCheck bez drugiego księgowania oraz czytelne powiadomienia.
3. Dom, remonty i instalacje jako następne moduły wykorzystujące wspólne miejsca, czynności, kalendarz i finanse.

Źródło zakresu: [ROADMAP.md](ROADMAP.md) i [SPECYFIKACJA_CALOSC.md](SPECYFIKACJA_CALOSC.md).
