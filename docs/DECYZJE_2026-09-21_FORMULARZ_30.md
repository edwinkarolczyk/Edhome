# EDHOME — decyzje Edwina po przeglądzie 30 pytań (21.09.2026)

> **Status:** zatwierdzone wymagania i zakres do zaprojektowania; nie jest to lista funkcji już wdrożonych w APK. Repozytorium: `edwinkarolczyk/Edhome`, gałąź `beta`. Żadne niewypełnione pytanie nie oznacza zgody na domyślne zachowanie.
>
> Ten rejestr ma **pierwszeństwo przed starszymi, sprzecznymi propozycjami** w SPECYFIKACJA_CALOSC.md i ROADMAP.md, dopóki nie zostaną ujednolicone. W szczególności: *dziewięć kafelków na stałe*, *oddzielna aplikacja tabletowa*, *każdy skan zawsze wymaga ręcznego potwierdzenia*, *rodzaj miejsca tylko z listy systemowej* — to wcześniejsze założenia, które NIE opisują aktualnej decyzji użytkownika.

## A. Zatwierdzone odpowiedzi — 1–30

| Nr | Zatwierdzona decyzja / stan |
|---|---|
| 1 | Wszystkie **sześć motywów** pozostają. |
| 2 | **Dowolna liczba kafelków**, dodawanie/ukrywanie/usuwanie skrótów z panelu i przywracanie; dziewięć nie jest limitem. Kafelek można w pełni edytować (cel, nazwa, ikona, kolor, rozmiar, pozycja, widoczność). |
| 3 | Kafelki w różnych rozmiarach, w tym duży zajmujący dwa pola. |
| 4 | Przeciąganie po **przytrzymaniu całego kafelka**, dodatkowo opcjonalny uchwyt i alternatywne sterowanie kolejnością. |
| 5 | Użytkownik **wybiera układ/tryb**; w EDHOME przełącza na **tryb tabletu**. To ten sam program i jedna instalacja, nie oddzielne APK. Tryb tabletu ma **ograniczony zakres opcji** wspólnych dla gospodarstwa (np. spiżarnia, skaner, zakupy, obowiązki, SUPLA zależnie od uprawnień); szczegóły zestawu i powrotu z trybu wymagają dalszego projektu. |
| 6 | Dwa formularze czynności: **szybki i zaawansowany**. |
| 7 | Trzy sposoby terminu czynności sezonowej: **konkretna data**, **przedział dat**, **sezon z proponowanym terminem**. |
| 8 | Liczenie następnego terminu od harmonogramu albo wykonania **osobno dla każdej czynności**. |
| 9 | Planer jako całość: grafiki zmianowe, wolne godziny, czas zadania, podział obciążeń. |
| 10 | Najpierw **powiązania kalendarza z modułami**, nie sama przebudowa jego wyglądu. |
| 11 | Użytkownik sam definiuje **nazwy i rodzaje miejsc**; typ nie jest ograniczony sztywnym słownikiem. |
| 12 | Pudełka w dowolnym miejscu, także **pudełko w pudełku**; trwałe ID/QR i ochrona przed cyklami hierarchii. |
| 13 | **Do ustalenia**: widoczność czynności miejsca nadrzędnego w podmiejscach. |
| 14 | **Dwa świadome tryby skanera:** „Dodaj produkty do spiżarni” -> skanuj seryjnie, rozpoznaj produkt z lokalnej kartoteki; gdy nieznany, zaproponuj dane z legalnej bazy open source i utworzenie karty. „Wyciągnij” -> pokaż aktualny stan, domyślnie ustaw -1 szt./paczka i po konfigurowalnym czasie bez zmiany zatwierdź odjęcie; zmiana ilości resetuje odliczanie. **Ponowny odczyt tego samego kodu podczas trwającego odliczania ignorować**. Dodać Anuluj/Cofnij i historię, uniknąć wielokrotnego odjęcia po podwójnym skanie. Długość czasu ustawia użytkownik; nie wybrano liczby sekund. |
| 15 | Przy równoległej zmianie w remanencie **zaproponować ponowne sprawdzenie zmienionych produktów**, zachować resztę wyników i nie nadpisywać nowszego stanu. |
| 16 | Godziny ciszy **osobno dla domownika i rodzaju powiadomienia**. |
| 17 | Niewykonana czynność: **stałe oznaczenie na panelu do potwierdzenia**. Nie utożsamiać z niekończącym się spamem powiadomień. |
| 18 | Zakupy mogą wisieć jako **„Do dodania do spiżarni / wybranego miejsca”**. **Miejsce docelowe wybiera się przy zakupie/pozycji zakupu**, nie jest sztywno przypisane do produktu. Nazwa „Spiżarnia” to opcjonalny przykładowy cel, użytkownik może tworzyć i przemianowywać miejsca. |
| 19 | Wszystkie trzy kopie: ręczny eksport JSON, automatyczna kopia lokalna, automatyczna kopia na NAS. Nie obiecywać NAS w już wydanym APK. |
| 20 | Użytkownik potwierdza, że aktualizacje **działają i zachowują dane** w jego dotychczasowym teście; osobno testować kolejne migracje. |
| 21 | Osobne profile na telefonach; **tablet jako współdzielony tryb tej samej aplikacji**, z ograniczonymi opcjami, bez nieuprawnionego kopiowania prywatnych finansów. |
| 22 | Połączenie jest wymagane dla wybranych operacji: **przenoszenie przedmiotów między urządzeniami**, **sterowanie SUPLA i energią** oraz **rozliczenia wspólnego budżetu**. Inne operacje nie zostały tu uznane za automatycznie wymagające sieci; konkretny transport/zakres do projektu. |
| 23 | Wspólny budżet pokazuje **wkłady każdej osoby oraz wspólne sumy**, nie upublicznia całej prywatnej historii. |
| 24 | EDHOME **proponuje rzeczywistą transakcję**, użytkownik ją zatwierdza; sama prognoza/rezerwacja to nie zaksięgowany wydatek. |
| 25 | **PayCheck** jako pierwszy duży kolejny moduł; równolegle domykanie fundamentów i istniejących przepływów. Dopisek „25” w uwagach nie zmienia odpowiedzi. |
| 26 | SUPLA od **listy urządzeń i ich rzeczywistych aktualnych stanów**, potem bilans energii i wykresy. |
| 27 | Docelowo automatyka PV, ale **etapami**: najpierw odczyt, następnie bezpieczne uprawnione działania, później automatyzacje po audycie sprzętu. |
| 28 | **Do ustalenia**: stała kolejka kontra profile sezonowe/dynamiczne priorytety PV. |
| 29 | Rozwijać **wspólny fundament równolegle z funkcjami**, nie zamrażać całości na samą architekturę. |
| 30 | Główna obawa użytkownika: **brak powiązań między modułami**. Każdy kolejny inkrement ma wskazywać istniejące obiekty, źródło prawdy, relacje, wpisy historii, migrację/backup i testy między modułami. |

## B. Krytyczne przepływy — doprecyzowanie

### Tablet to tryb EDHOME, nie osobna aplikacja

W ustawieniach/interfejsie EDHOME użytkownik może przejść na **tryb tabletowy** dostosowany do wspólnego korzystania z urządzenia: duże kafelki i wybór ograniczonych opcji. Nie tworzyć nowego repo/klienta/APK „EDHOME Tablet”. Tryb wyglądu i nawigacji sam w sobie **nie nadaje uprawnień do prywatnych danych** i nie wystarcza samo ukrycie ekranów; kontrola dostępu przed synchronizacją/przechowywaniem pozostaje wymogiem.

### Tryb seryjnego skanowania — odjęcie

1. Użytkownik wybiera „Wyciągnij”; skaner pokazuje rozpoznany produkt oraz aktualny stan.
2. Domyślna ilość do odjęcia: 1 szt./paczka; uruchamia się **czas ustawiony przez użytkownika**.
3. Gdy użytkownik zmienia ilość, licznik startuje ponownie; po upłynięciu czasu od ostatniej zmiany następuje jedno zaksięgowanie operacji.
4. Odczyt **tego samego** kodu w trakcie licznika nie dodaje następnej sztuki i nie restartuje licznika. Obsługa innego kodu w tym czasie wymaga bezpiecznej reguły interfejsu (do decyzji).
5. Wyświetlić Anuluj przed zatwierdzeniem i umożliwić korektę/Cofnij po nim; ruch z trwałym ID, niepowtarzalny po restarcie i synchronizacji.
6. Nie dopuszczać ujemnego stanu bez osobnej decyzji; nieobecna ilość ≠ 0.

**Zmiana względem starszej specyfikacji:** zwykły skan w trybie „Wyciągnij” może *po odliczeniu* wykonać operację bez dodatkowego kliknięcia. Starsze „sam skan nigdy nie zmienia stanu” opisuje teraz skan **bez trybu** oraz etap **przed końcem odliczania**, a nie wymóg wiecznego ręcznego potwierdzania każdej sztuki.

### Zakupy → oczekujące odłożenie

Kupione pozycje mogą pozostać w kolejce **„Do dodania do [miejsce]”**. Cel zapisuje się dla **konkretnej pozycji zakupu**; można wybrać dowolne miejsce z globalnej hierarchii. Nie przypisywać automatycznie lokalizacji na stałe do definicji produktu. Skan/wybór produktu zmienia stan magazynu tylko raz, zachowuje powiązanie z zakupem i historię.

### Spójność pierwsza

Dla czynności → kalendarza, zakupu → spiżarni, magazynu → QR, celu OC → PayCheck oraz SUPLA → Energia wymagamy spójnych identyfikatorów i jawnych zdarzeń. Unikać „dwóch tabel tego samego faktu” i dublowania transakcji.

## C. Otwarte — użytkownik nie rozstrzygnął

- **13:** reguła dziedziczenia widoczności czynności z miejsca nadrzędnego na jego dzieci.
- **28:** priorytety energii/PV: kolejka stała, sezonowa, dynamiczna czy połączenie.
- **14 — szczegół:** konkretne domyślne sekundy, zachowanie przy odczycie innego produktu przed końcem odliczania, reguły obsługi opakowań częściowych i synchronizacji.
- **5 — szczegół:** lista uprawnionych opcji tabletu, sposób wejścia i wyjścia z trybu; ten sam APK jest zatwierdzony.
- **22 — szczegół:** co dokładnie oznacza „między urządzeniami” i które dane mają czekać na połączenie vs działać lokalnie; nie wyciągać stąd zakazu całego offline magazynu.
- Potrzebny audyt licencji i offline cache katalogu kodów produktów przed oznaczeniem go jako zintegrowany.

## D. Status wykonania — oddzielny od decyzji

Ten plik zatwierdza **zakres**, nie implementację. Ostatnio sprawdzono kod `0.3.4-beta.1` i nowszy udany workflow GitHub Actions po wcześniejszym teście SQLite v10/v11. Brak testu na konkretnym urządzeniu dla nowych ustaleń; moduły PayCheck, skaner kamery, pełne SUPLA i synchronizacja są pracą przyszłą. Aktualizacje użytkownik zgłasza jako działające i zachowujące dane — odnotować jako test użytkownika, nie automatyczną gwarancję migracji każdej wersji.

## E. Powiązane dokumenty

- [Pełna specyfikacja](SPECYFIKACJA_CALOSC.md)
- [Roadmapa](ROADMAP.md)
- [Architektura](ARCHITEKTURA.md)


## F. Nadrzędne doprecyzowanie — wszystkie funkcje w edytowalnych kafelkach (22.09.2026)

**Obowiązująca decyzja Edwina; §26 pełnej specyfikacji oraz aktualna roadmapa mają pierwszeństwo przed zapisem historycznym 0.3.3-beta.2 o dziewięciu „systemowych” kafelkach i zakazie zmiany celu.** Układ 3×3 na screenshotach 0.5.0-beta.2 nie jest docelowym limitem. Nie oznacza to, że funkcja została już wdrożona.

- Wszystkie wejścia modułowe, także **Minutniki urządzeń, Lista zakupów, PayCheck (wspólny budżet/osobisty po uprawnieniach), Odpady i terminy wystawienia, wszystkie Czynności**, oraz Diagnostyka Beta, mają być możliwymi do dodania kafelkami. Nie tworzyć osobnego, stałego menu skrótów pod dziewięcioma kafelkami.
- **Pełny edytor każdego skrótu:** wybór celu otwierania (moduł/widok/dozwolony obiekt), własna nazwa, ikona, kolor, rozmiar, pozycja i widoczność. Zmiana celu jest wyraźnie dozwolona; nie zmienia jednak ID ani treści rzeczywistego obiektu/modułu. Ta sama funkcja może mieć kilka skrótów.
- Przytrzymanie całego kafelka → Edytuj / Przesuń / Ukryj/Usuń skrót, przeciągnij w górę lub dół; lista przewijana i responsywna, dziewięć nie jest limitem ani liczbą nieusuwalnych pozycji. Usunięcie skrótu nigdy nie kasuje danych funkcji.
- Własne układy telefonu i współdzielonego tabletu, backup/migracja konfiguracji; dozwolone cele i dane sprawdza się na poziomie uprawnień, nie tylko ukrycia kafelka. Diagnostyka tylko Beta; prywatne PayCheck nie staje się dostępne na tablecie przez przypisanie kafelka.
- Odbiór: co najmniej 15 skrótów, dowolna zmiana celu, nazwy, ikony, koloru, położenia i rozmiaru, uruchomienie minutników/zakupów/PayCheck **z kafelka**, ukryj/przywróć i zachowaj całość po restarcie/odtworzeniu.
