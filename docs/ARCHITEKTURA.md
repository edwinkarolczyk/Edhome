# Architektura EDHOME — zarys 0.1

**Etap:** projekt, nie implementacja. Cel: przygotować rozbudowę bez dodawania pustych pól typu `zmienna1` i bez bezpośredniego splatania modułów.

## Wspólny rdzeń

1. **Tożsamość:** trwałe wewnętrzne UUID obiektów; osobne czytelne numery np. `BOX-012`, `ITEM-045`, `LOC-003`. Numery pudełek są unikalne w gospodarstwie. Nie kodujemy lokalizacji w QR.
2. **Atrybuty:** wspólne pola podstawowe + typowane rozszerzenia per typ obiektu, z wersjonowaniem schematu. Dodatkowe dane są walidowane; brak anonimowych wolnych kolumn.
3. **Relacje:** typowane i walidowane powiązania: `located_in`, `contained_in`, `belongs_to`, `concerns`, `funded_by`, `performed_by`. Powiązania mogą być opcjonalne i wielokrotne, ale hierarchia lokalizacji nie może mieć cykli.
4. **Zdarzenia:** identyfikator zdarzenia, czas, autor, źródło, typ, wersja danych; historia operacji i odporność na ponowne dostarczenie.
5. **Reguły:** jawne następstwa zdarzeń między modułami; nie modyfikować sald ani stanów magazynu przez sam odczyt kalendarza.
6. **Planer:** strefa czasowa, dostępność, zmiany pracy + wyjątki, okna ciszy, czas trwania, trzej kandydaci terminu, odpowiedzialny oraz uczestnicy.
7. **Autoryzacja:** gospodarstwo, domownicy, prywatne i wspólne przestrzenie danych, ograniczenia widoczności przed synchronizacją, nie tylko w UI.

## Moduły mają własne dane, ale wspólne interfejsy

- **Czynność:** samodzielna definicja; 0..N opcjonalnych obiektów powiązanych; osobno reguła powtarzania i każda konkretna instancja wykonania.
- **Powtarzanie:** co dzień/tydzień/miesiąc/rok/N jednostek; przed/na konkretną porę roku; od planowanego terminu lub od rzeczywistego wykonania; według licznika przebiegu/motogodzin tam, gdzie dostępny. Sezony i wyprzedzenie konfigurowalne.
- **Magazyn:** przedmiot bez pudełka nadal może mieć lokalizację; pudełko ma lokalizację i zawiera przedmioty. Przeniesienie pudełka zmienia pochodną lokalizację zawartości bez przepisywania każdego wpisu. Wypożyczenia odróżniają właściciela i fizyczną lokalizację; ilość opcjonalna, brak to nie zero.
- **QR:** skaner przyjmuje tryb działania (znajdź, dodaj, wyjmij, przenieś, pożycz/oddaj); kod koduje wyłącznie typ i stałe ID obiektu, ewentualnie wersję formatu. Obiekt i bieżącą lokalizację rozwiązuje lokalna baza.
- **Kalendarz:** wspólny widok zdarzeń z modułów, bez mnożenia kopii. Prywatne wydarzenia innych domowników udostępniają wyłącznie zajętość; ich tytuły i finanse nie opuszczają właściwej prywatnej przestrzeni.
- **PayCheck:** właściciel operacji finansowych. Rezerwacja/odkładanie środków ≠ nowy koszt. Polisa OC pojazdu może odwoływać się do celu oszczędnościowego; import powiadomienia → oczekujące potwierdzenie → uzgodnienie z wyciągiem; idempotencja i kontrola transferów wewnętrznych.
- **SUPLA:** adapter, najpierw odczyt; działania możliwe offline tylko jeśli konkretny sprzęt/protokół to umożliwia; nie wymuszać dostępu do chmury.
- **Powiadomienia:** lokalne, profil domownika, godziny ciszy, poziom krytyczny wyłącznie po jawnej konfiguracji; ograniczenia Androida obowiązują. Odroczenie i ponaglenie do potwierdzenia bez spamu.

## Synchronizacja i przechowywanie

- Lokalna transakcyjna baza dla każdego urządzenia, bez wymogu sieci do codziennych operacji.
- Trwała kolejka zmian, idempotencja, deduplikacja, polityki konfliktów per typ danych oraz sprawdzana kompatybilność wersji protokołu/schematu.
- Prywatne finanse nie mogą być kopiowane na wspólny tablet wyłącznie po to, by je ukryć w interfejsie.
- Jeden tablet przenośny: panel i pełna aplikacja, PIN do całości, wygaszanie po bezczynności, edytowalny układ kafelków.
- Przyszłościowo: szyfrowany backup na domowym serwerze/NAS; jego brak nie blokuje aplikacji.

## Migracje i integralność

Każda wersja bazy ma numer schematu, procedurę migracji i weryfikację. Eksport obejmuje dane, relacje, historię, ustawienia i wersje. Usunięcie/archiwizacja pojazdu czy pudełka nie może kasować historycznych kosztów i zadań. W szczególności nie wolno przypadkowo księgować kosztów ponownie ani kasować nieprzypisanych przedmiotów.

## Decyzje techniczne do audytu przed implementacją

Stos Android, mechanizm lokalnego odczytu SUPLA, legalnie dostępna offline baza upraw, stan kodu PayCheck/Trener 2, model synchronizacji przy kilku telefonach, bezpieczeństwo PIN i kluczy, dokładne ograniczenia powiadomień bankowych/Androida i instalowania aktualizacji z Google Play. Niczego tu nie ogłaszamy jako wdrożone.

## Adaptery skanowania i operacje magazynowe

Czytnik kamery tabletu ma odróżniać kody kreskowe produktów (np. EAN/UPC, mapowane do lokalnego katalogu) od wewnętrznych QR obiektów (typ + trwałe ID). Skan zwraca **odczytany obiekt**, natomiast cel operacji jest osobnym parametrem; sam odczyt nie zmienia magazynu. Dodanie/wyjęcie wymaga potwierdzenia, zapisuje idempotentną operację oraz historię. Nieznany produkt może otrzymać kartę, bez wymogu internetu. Gniazdo adaptera zewnętrznego czytnika pozostaje na przyszłość. Tablet musi móc modyfikować układ kafelków bez ingerowania w wspólny model magazynu.

Więcej przykładów i kontekst produktu: [SPECYFIKACJA_CALOSC.md](SPECYFIKACJA_CALOSC.md).

## Remanent spiżarni — odrębny proces

Remanent jest cykliczną czynnością połączoną z **sesją liczenia**, a nie serią automatycznych operacji „wyciągnij”. Sesja zapisuje zakres, użytkownika, znacznik czasu, snapshot oczekiwanych stanów, postęp i indywidualne wyniki: zgodne / nowa ilość / brak / pominięte. Stan nieokreślony nie jest zerem. Wynik „zgadza się” rejestruje datę fizycznego potwierdzenia.

Na końcu powstaje propozycja korekt, którą użytkownik **osobno zatwierdza**; wtedy transakcyjnie aktualizować stany i historię. Każda korekta ma trwałe ID operacji, powiązaną sesję, ilość przed/po, jednostkę i wykonawcę; ponowne dostarczenie przez synchronizację nie może wykonać jej drugi raz. Zmiany na drugim urządzeniu od chwili snapshotu wymagają uzgodnienia konfliktu przed zastosowaniem różnic. Sesję można wznowić i zachować pominięte pozycje.

Pełny scenariusz i opcje UI opisuje [SPECYFIKACJA_CALOSC.md](SPECYFIKACJA_CALOSC.md#11-nowe-ustalenie--kreator-okresowego-remanentu-spiżarni-na-tablecie).


## Moduł Energia — bilans, priorytety i sterowanie (plan)

Adaptery danych (PV, licznik sieciowy, SUPLA, czujniki temperatury) przekazują wartości z jednostką, znacznikiem czasu i informacją o jakości/dostępności. Warstwa bilansu rozdziela produkcję, zużycie, import i eksport, a braków nie traktuje jak 0. Warstwa reguł dysponuje tylko **potwierdzoną, aktualną** nadwyżką i uprawnionymi odbiornikami; osobny adapter wykonawczy wysyła polecenia dopiero w trybie wyraźnie włączonym przez użytkownika. Odbiorniki opisują limity elektryczne/termiczne, warunki pracy, kolejność priorytetów, minimalne czasy/histerezę, przyczynę odrzucenia i stan wykonania.

Automatyka wymagająca ciągłego działania nie może polegać na procesie Androida pozostającym w tle — docelowo sterownik/bramka lokalna. Awaria danych/sieci nie może prowadzić do przełączania na podstawie nieaktualnych wskazań. Historia, powiadomienia, Czynności i PayCheck korzystają z jednego potwierdzonego zdarzenia; wyliczony koszt nie jest drugą transakcją księgową. Lokalność API SUPLA, typ czujników, sposób sterowania CWU/PC i zgodność z warunkami przyłączenia wymagają audytu. Zob. [SPECYFIKACJA_CALOSC.md](SPECYFIKACJA_CALOSC.md#21-edhome--panel-zarządzania-energią-i-priorytetami-nadwyżek-pv-uzgodnienie).

## SUPLA — trzy adaptery zamiast jednego niejawnego połączenia

`CloudDiscoveryReader` (HTTPS API + OAuth2), `CloudMqttReader` (TLS, opcjonalny odbiór bieżących zmian) i `LocalDeviceAdapter` (tylko potwierdzone protokoły LAN) mają osobne statusy i identyfikację źródeł. Wszystkie przekazują do wspólnej warstwy pomiarów trwałe mapowanie do kanału/obiektu, jednostkę, czas, ważność i status połączenia. Nie wyliczać ani nie sterować na podstawie nieświeżych wskazań. Pierwszy etap jest wyłącznie read-only, a adapter poleceń powstanie później z uprawnieniami, audytem, limitami i niezależnym zabezpieczeniem urządzenia.

Dla aplikacji natywnej zweryfikować OAuth2 Authorization Code + PKCE i redirect/deep link zgodny z dostawcą; nie osadzać haseł, PAT lub client secret w kliencie Android ani w publicznym repo. Lokalny sterownik ciągłych automatyzacji nie może zależeć od aktywności Androida. Chmurowy MQTT nie dowodzi działania offline.
