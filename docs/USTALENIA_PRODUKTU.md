# EDHOME — ustalenia produktowe

Wszystkie wymienione funkcje są **zakresem docelowym**, nie deklaracją wykonania.

## UI i urządzenia
- EDHOME — Idea by Edwin. Trzy motywy: Grafitowy (domyślny), Leśny, Jasny. Własna kolejność, widoczność i dobór kafelków. Zaokrąglone ikony Stable i Beta; beta z bardzo widocznym oznaczeniem.
- Android, docelowo Google Play. Telefon ok. 6,4 cala: pełnoekranowy, przewijalny kalendarz bez stałego dolnego paska. Miesiąc domyślnie, od poniedziałku, weekendy zawsze, krótkie nazwy, kolory wg modułu, dzień/tydzień/agenda.
- Tablet przenośny: panel i pełna aplikacja, PIN do całej aplikacji, wygaszanie po bezczynności, własne kafelki. Przykładowo zakupy, minutniki prania/suszenia, spiżarnia, zadania, SUPLA, dodawanie głosowe; głos offline zależny od możliwości urządzenia.

## Czynności i planer
- Czynności mogą istnieć samodzielnie („Zebrać winogrona”), być przypięte do rzeczy, miejsc, roślin, pojazdów itd., albo mieć wiele powiązań.
- Definicja, reguła powtarzania i konkretne wykonanie to osobne pojęcia. Częstotliwość jednorazowa, dzień/tydzień/miesiąc/rok/N, przed każdą z pór roku, według użycia; terminy sugerowane, planowane i faktyczne rozdzielone.
- Grafiki zmianowe z wyjątkami; po podaniu czasu zadania EDHOME proponuje 3 terminy, użytkownik wybiera. Domyślny jeden odpowiedzialny, 2+ możliwych wykonawców branych pod uwagę wg wolnego czasu, historii i preferencji; zadania wymagające 2 osób szukają wspólnego okna.
- Stałe/rotacyjne/wg dostępności obowiązki; zaległe zadania → propozycja nowego terminu bez samowolnego przestawiania; checklisty, szablony, historia i „co mogę zrobić teraz?”.
- Powiadomienia z godzinami ciszy per osoba; tylko jawnie włączone krytyczne mogą próbować ominąć ciszę, w granicach uprawnień Androida. Przypomnienia do potwierdzenia.
- Wywóz odpadów wg frakcji: ręczny harmonogram, import gminny w przyszłości. Minutniki urządzeń, kilka równolegle, zachowanie po wznowieniu, osobny etap „wyjmij pranie”, łańcuch pranie → suszenie.
- Cztery sezony z konfigurowalnymi zakresami; prace „przed zimą”, „przed latem” itd. oraz historia z roku na rok. Bez automatycznego zalecania niebezpiecznych prac na dachu.

## Magazyn i QR
- Globalna lista rzeczy; pudełka, własne nazwane miejsca (np. Piwnica → Pod schodami), bez obowiązkowych regałów/półek. Rzeczy mogą nie mieć pudełka.
- Ilość opcjonalna (dokładna/orientacyjna/brak), zdjęcia i wyszukiwarka prowadząca do właściwego pudełka, miejsca lub przedmiotu.
- Unikalne identyfikatory i QR pudełek, przedmiotów/narzędzi i miejsc; tylko stały identyfikator na naklejce, lokalizacja pobierana z bazy. QR miejsca pokazuje co jest przypisane/oczekiwane; warto zapisywać ostatnie fizyczne potwierdzenie.
- Skanowanie w trybie wybranej czynności: znajdź, dodaj, wyjmij, przenieś, pożycz, oddaj. Przedmioty wypożyczone komuś i pożyczone od kogoś; opcjonalne terminy zwrotu i historia.

## Finanse, pojazdy, ogród, energia
- Dwie lub więcej osób: osobiste PayCheck + wspólne finanse. Prywatne pozycje kalendarza drugiej osoby widoczne jako „Zajęty”, bez szczegółów.
- Wykrywanie wpływów/wydatków na telefonie właściciela z powiadomień bankowych do **potwierdzenia**, uzgodnienie dziennym importem wyciągu, osobne konta i reguły; wpis ręczny jako opcja, nie domyślna ścieżka. Nie kopiować innych powiadomień ani nie dublować transakcji.
- Pojazdy: serwisy, oleje, motogodziny/przebieg, komplety opon (rozmiar, DOT, zakup, bieżnik, stan, miejsce), OC i historia. OC może być powiązane z celem finansowym, wyliczając brakującą kwotę i proponowane miesięczne oszczędzanie.
- Ogród: lokalna baza roślin po sprawdzeniu licencji, siew/sadzenie/zbiór, odmiany, harmonogram i historyczne plony; później grządki/płodozmian.
- Energia/SUPLA: PV, ogrzewanie, CWU i media, początkowo odczyt. Dostępność lokalnego sterowania/odczytu wymaga weryfikacji konkretnego sprzętu.
- Wspólna lista zakupów, zapasy spiżarni, domowe przeglądy, remonty, gwarancje, dokumenty i informacje awaryjne.

## Dystrybucja
`main` stable, `beta` rozwój; Beta DEV z osobnym identyfikatorem/danymi; możliwa beta przez testowy kanał Google Play. Stable sprawdza aktualizację po uruchomieniu, pokazuje zmiany i „przypomnij później”. Beta DEV może często sprawdzać metadane i pobierać buildy; Android/Google Play sterują faktyczną instalacją. Testy przed publikacją, migracje i kopie bezpieczeństwa.

## Granice zakresu
Na razie nie modyfikować Trenera 2, PayCheck, WM, SUPLA ani innych repozytoriów. EDHOME ma być samodzielnym projektem; każda zależność zewnętrzna wymaga audytu licencji/kompatybilności.
