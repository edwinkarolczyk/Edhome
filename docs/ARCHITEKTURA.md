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
