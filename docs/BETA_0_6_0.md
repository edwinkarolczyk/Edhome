# EDHOME 0.6.0 — Pojazdy

## 0.6.0-beta.1 — pierwszy działający inkrement

- Karty pojazdów: nazwa, rejestracja, aktualny przebieg, terminy OC i przeglądu oraz notatka. Własny kafelek „Pojazdy” w edytowalnym panelu, bez limitu dziewięciu skrótów.
- Terminy OC i przeglądu są **czytane bezpośrednio z kart pojazdów** przez istniejący kalendarz (dzień, tydzień, miesiąc, agenda); nie tworzą drugiej czynności ani transakcji finansowej. Widok pojazdu pokazuje „za ile dni / po terminie”.
- Historia wykonanych prac: serwis, opony i inne; data, opcjonalny przebieg, opis. Wpis zawiera trwały identyfikator operacji, jego ponowne dostarczenie jest ignorowane. Aktualizacja przebiegu pojazdu i zapis historii odbywają się w jednej transakcji; cofnięcie licznika jest odrzucane. To **historia prac**, nie pełna ewidencja kompletów opon.
- SQLite **23 → 24**: nowe `vehicles`, `vehicle_events`; import kopii ze starszych wersji tworzy obie puste tabele, nie zmieniając dotychczasowych danych. Kopia v24 obejmuje pojazdy i wpisy serwisu; import weryfikuje daty, typy, referencje i duplikaty `operation_id`.
- Nie ma fikcyjnych danych pojazdów, automatycznego przedłużenia OC ani dopisywania kosztów do PayCheck. Ten etap nie obsługuje jeszcze numeru polisy, załączników, historii polis, osobnych kompletów opon, kosztów czy wiązania z celem PayCheck.
- Testy: walidacja Java, migracja SQLite v23→v24 z zachowaniem danych i regresja schematów historycznych, kontrakt backupu i kalendarza, podpisany APK przez CI; test aktualizacji na telefonie jest osobnym odbiorem.
- Stable `main` bez zmian; nie scalać bez każdorazowej wyraźnej akceptacji Edwina.

## Kolejne inkrementy 0.6 — zakres, nie wdrożenie

1. Ewidencja opon jako rzeczywistych kompletów z miejscem, DOT, bieżnikiem i historią zmian; jeden potwierdzony ruch dla zmiany kół.
2. Polisy i dokumenty z historią, odnowienia OC, przypięcie terminu do celu PayCheck bez drugiego księgowania oraz czytelne powiadomienia.
3. Dom, remonty i instalacje jako następne moduły wykorzystujące wspólne miejsca, czynności, kalendarz i finanse.

Źródło zakresu: [ROADMAP.md](ROADMAP.md) i [SPECYFIKACJA_CALOSC.md](SPECYFIKACJA_CALOSC.md).
