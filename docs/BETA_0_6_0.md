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

## Kolejne inkrementy 0.6 — zakres, nie wdrożenie

1. Doprecyzowanie ewidencji opon po testach beta.2; ocena powiązań z innymi rzeczami w magazynie bez kopiowania stanu.
2. Polisy i dokumenty z historią, odnowienia OC, przypięcie terminu do celu PayCheck bez drugiego księgowania oraz czytelne powiadomienia.
3. Dom, remonty i instalacje jako następne moduły wykorzystujące wspólne miejsca, czynności, kalendarz i finanse.

Źródło zakresu: [ROADMAP.md](ROADMAP.md) i [SPECYFIKACJA_CALOSC.md](SPECYFIKACJA_CALOSC.md).
