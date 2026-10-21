# EDHOME 1.0 — kontrakt diagnostyki i testów
Data: 2026-10-21. Zakres: Android i Desktop, gałąź beta. Stable (main) bez zmian do bramki wydania.

## Zasady wydania
- Kończymy tylko zatwierdzoną funkcjonalność niezbędną do parytetu Android/Desktop. Nowe propozycje trafiają do 1.0 Plus.
- Zanim 1.0 trafi do stable, w obu aplikacjach obowiązuje ten sam kontrakt diagnostyczny i pozytywne testy.
- Dla stable 1.0 trzeba osobno włączyć diagnostykę (obecny wariant stable Android ma DIAGNOSTICS_ENABLED=false). Nie publikować stable z wyłączonym zbieraniem błędów.
- Logi mają działać offline i lokalnie, bez zależności od NAS, internetu i aktywnej synchronizacji.

## Minimalny rekord diagnostyczny
- Czas z datą i strefą, platforma, wersja, identyfikator zdarzenia, poziom INFO/WARN/ERROR/CRASH, wynik, bezpieczny kontekst działania.
- W przyszłym jednolitym formacie dodać identyfikator operacji (correlation ID) łączący zdarzenia Android ↔ Desktop, czas trwania i kod błędu. Nie używać danych finansowych ani osobowych jako identyfikatora.
- Rejestrować rozpoczęcie, sukces albo wycofanie błędnych operacji zmieniających dane: zapis/edycja/usuwanie, import bankowy, grupowanie rozliczeń, ZIP/restore, LAN, backup, aktualizacja, migracje, alarmy, planowanie i timery.
- Dla błędu zapisać typ, bezpieczne ramki stosu oraz kod modułu. Nie zapisywać opisów zadań, haseł, PIN, tokenów, treści przelewów, kwot, numerów kont, surowych ścieżek lub pełnych komunikatów z wyjątku.
- Nie logować każdego dotknięcia ekranu. Sukces drobnej operacji nie musi być wpisem, jeśli nie ma znaczenia dla diagnozy.

## Najważniejsze bramki jakości
1. P0: brak utraty lub duplikowania danych Budżetu, PayCheck i ZIP, w tym rozliczeń 2→1 / 1→2, nadpłat, przerwanego zapisu i rollbacku.
2. P0: porównanie danych Android ↔ Desktop przed zatwierdzeniem oraz po synchronizacji. Samo połączenie API nie oznacza zgodności baz.
3. P0: każdy błąd importu, migracji, synchronizacji, konfliktu, zapisu i przywrócenia kopii ma zapis w diagnostyce z jednoznacznym wynikiem; przy błędzie nie ma fałszywego komunikatu „Zapisano”.
4. P1: pozostałe moduły — czynności, projekty, terminarze, magazyn, skaner QR/NFC, pojazdy, odpady i powiadomienia — mają obsługę niepowodzeń z logowaniem.
5. P1: test awarii wątku, braku sieci, pełnego dysku, starego ZIP, uszkodzonego pliku i utraty połączenia w trakcie transferu.
6. P1: w Ustawieniach Desktop jeden eksport pełnej historii obu urządzeń, z informacją o zakresie, wersjach i braku telefonu. Na telefonie eksport bez Desktopu.
7. P1: logi telefonu można skasować dopiero po trwałym zapisie na Desktopie i ACK SHA-256; przerwanie nie gubi nieprzesłanych wpisów.
8. P1: audyt istniejących `catch(... ignored)`, `printStackTrace` i obsługi callbacków — kwalifikacja błędów istotnych zamiast ślepego logowania każdej niegroźnej odmowy.
9. Po poprawkach: CI Android i Desktop PASS, testy na rzeczywistym telefonie i Windows, scenariusze offline, eksport logów, ZIP roundtrip, aktualizacja bez odinstalowywania.
10. Bramka stable 1.0: brak otwartych P0, lista P1 zaakceptowana, raport zgodności modułów, powtarzalne testy i możliwość odzyskania danych.

## Stan wdrożenia 2026-10-21
- Istnieją logi rotacyjne 1 MB + 1 MB, eksport TXT i archiwum diagnostyki Android na Desktop przez Hub.
- Poprawiono uruchamianie handlera Android w Application i idempotencję init; Desktop przechwytuje nieobsłużone wyjątki globalnie, bez zapisywania wiadomości wyjątku.
- Nadal NIE gwarantujemy zapisania każdej obsłużonej awarii funkcjonalnej. Brakujące punkty powyżej są zadaniami odbiorowymi 1.0, nie zakończonymi funkcjami.
- Stable main bez zmian; testy i instalacje fizyczne po tym commicie do potwierdzenia.
