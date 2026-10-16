# EDHOME 1.0 — audyt P0/P1: Budżet, PayCheck i historia bankowa

**Data audytu:** 2026-10-16 (przygotowanie prac na 17 października)  
**Gałąź:** `beta`, **bez zmian w `main`**  
**Stan wejściowy:** Android Beta **0.8.0.119 / 311**, Desktop Beta **0.7.0.121**  
**Rodzaj dowodów:** analiza kodu GitHub + przegląd dostępnych testów i ostatniego CI Android Beta **PASS 117/117**. **Bez** nowych wyciągów użytkownika, bez wykonania importu bankowego na urządzeniu i bez sprzętowego testu synchronizacji LAN.  
**Werdykt:** **warunkowe NO-GO dla produkcyjnego 1.0 finansów** do zamknięcia punktów wymagających walidacji. **Nie stwierdzono w tym audycie nowego, odtworzonego błędu salda na urządzeniu**, ale istnieją potwierdzone ograniczenia UI i ważne ryzyka spójności danych.

## Potwierdzone istniejące zabezpieczenia

1. **Jedno źródło planu**: `PaycheckMonthlyBudget` i `ensureBudgetPaycheckPending` zapewniają pozycje oczekujące powiązane z Budżetem; sam plan nie zmienia potwierdzonego salda.
2. **Potwierdzenie prawdziwej kwoty**: `BankEvidenceStore.matchBudgetActual` kopiuje do potwierdzonego `paycheck_transactions` rzeczywistą kwotę i datę z `bank_evidence_queue`. `BankBudgetSingleSettlement.confirm` sprawdza zmianę salda o dokładnie tę kwotę i przewiduje `repairUnlinked` bez powtórnego księgowania.
3. **Dwa przelewy do jednego i jeden do dwóch**: `BankBudgetGroupRules`, `BankBudgetGroupSettlement` i `PaycheckMonthlyBudget.matchBankParts/allocateSplit` istnieją; interfejs wymaga świadomego potwierdzenia. Reguły `BankBudgetGroupRules` przy 2→1 wymagają tego samego miesiąca, rodzaju i dat oddalonych o maksymalnie 7 dni, a suma musi być zgodna.
4. **Idempotencja importu i ochrona historii**: `BankEvidenceStore.ingest` ma transakcyjny zapis i sprawdzanie unikalnego `evidence_key`. Odrzucenie nieprzypisanej operacji pozostawia stan `dismissed`, by ponowny import nie przywrócił jej niepostrzeżenie.
5. **Nadpłaty i rozdział groszy**: `PaycheckBudgetCreditMath`, `PaycheckBudgetAllocationGuard`, `PaycheckMonthlyBudget.assertCreditConservation` pilnują limitów i niepozwalania na powtórne wykorzystanie tej samej kwoty.
6. **Bezpieczne odrzucenie niejednoznacznego Velo PDF**: `BankStatementVeloPdf.parseStatementRowsReport` policzy rozpoznane datowane wiersze, a przy rozpoznanych, ale nierozczytanych operacjach odrzuca import zamiast cicho zaksięgować niepełną listę. PDF-y obrazowe bez warstwy tekstowej nie są obsługiwane.

## Wyniki wymagające działania przed 1.0

### AUD-FIN-01 — P0, ryzyko integralności: dwie różne transakcje zapisu w rozliczeniach grupowych (DO WERYFIKACJI I NAPRAWY)

**Dowód:** `BankBudgetGroupSettlement.twoBankToOneBudget` i `oneBankToTwoBudgets` otwierają transakcję SQLite (`db.beginTransaction`) i wykonują potwierdzenia bankowe, a **przed zakończeniem transakcji** zapisują plan i historię przez `PaycheckMonthlyBudget.matchBankParts` / `allocateSplit` → `saveWithEvents` → `SharedPreferences.Editor.commit()`. SQLite i SharedPreferences nie tworzą jednej atomowej transakcji między magazynami.

**Ryzyko:** awaria procesu/dysku albo błąd kończący transakcję SQLite po skutecznym zapisie SharedPreferences może pozostawić plan ze wskazaniem operacji, której księgowanie zostało cofnięte. W drugą stronę też należy wykazać odzyskiwalność. **Nie jest to stwierdzenie odtworzonej utraty pieniędzy**, ale blokujące ryzyko dla hasła „bezpieczne finanse 1.0”.

**Zadanie:** test przerwania na każdym etapie 2→1 i 1→2 + mechanizm naprawy uzgodnienia planu z potwierdzonymi rekordami, odporność na ponowny import. Porównać z `BankBudgetSingleSettlement.repairUnlinked`, które obecnie dotyczy przede wszystkim 1→1.

### AUD-FIN-02 — P1, potwierdzone ograniczenie: propozycje grupowe obejmują tylko 300 otwartych operacji

**Dowód:** `MainActivity.showBankBudgetProposals(int offset)` tworzy `groupRows` z `for(cursor=0;cursor<Math.min(total,300);cursor+=100)`; `appendBankBudgetGroupProposals` ma dodatkowo `maxSuggestions=40`.

**Skutek:** przy długiej historii (np. ponad 300 nierozliczonych przelewów) właściwa para 200+300 zł może istnieć w SQLite, ale **nie pojawić się** w pulsujących propozycjach 2→1, jeśli wypadnie poza pierwsze 300 rekordów. To nie oznacza braku importu czy utraty pieniędzy. Wskazanie odpowiedniego miesiąca i paginacja/grupowanie powinna obejmować całą otwartą historię bez obciążenia interfejsu.

**Zadanie:** wyszukiwanie grupowych propozycji po miesiącu i po filtrze kandydatów w SQLite, stronicowanie, test z 301+ przelewami i parą na końcu.

### AUD-FIN-03 — P1, potwierdzone ograniczenie: VeloBank PDF nie jest automatycznie uzgadniany

**Dowód:** `MainActivity.autoSettleImportedBankEvidence` wyklucza wpisy `"velo_pdf"` zarówno podczas tworzenia miesięcy, jak i dla kandydatów automatycznych (`stats.review++`). Dzięki temu parser PDF nie księguje automatycznie danych o mniej pewnym formacie.

**Skutek:** nawet jednoznaczny import VeloBanku może wymagać ręcznej akceptacji po wyświetleniu propozycji. To jest celowy bezpiecznik, **nie błąd podwójnego księgowania**. Utrzymać bezpieczny tryb do czasu dostarczenia rzeczywistych PDF-ów; uczytelnić ten fakt w UI.

### AUD-FIN-04 — P1, rzeczywista kompatybilność parserów wymaga testów na bankowych próbkach

- Ogólne CSV `BankStatementCsv.parse` wymaga daty, kwoty, **stabilnego identyfikatora transakcji** oraz opisu; parser nie przyjmuje standardowego pola cytowanego obejmującego wiele fizycznych linii.
- mBank `BankStatementMbank` rozpoznaje charakterystyczny nagłówek `#Data księgowania` i saldo po operacji; testy muszą objąć różne warianty XLSX/CSV, kodowania i strony pliku.
- VeloBank PDF wymaga wyciągu **z tekstem możliwym do zaznaczenia**; algorytm rozpoznaje wiersze według dat/wzorów i może nie znać kolejnego wariantu układu wydruku. Na próbkach należy porównać **liczbę operacji, sumę wpływów, sumę wydatków oraz pierwszą/ostatnią datę** z informacjami z banku. Nie deklarować pełnego odczytu jedynie dlatego, że parser nie zgłosił błędu.

### AUD-FIN-05 — P1, rozbieżne reguły prezentacji propozycji Android vs Desktop

**Dowód:** Android `BankBudgetMatchRules.descriptionIdentifies` / `autoSettleImportedBankEvidence` korzysta m.in. z kwoty, rodzaju, miesiąca i wyróżniającej nazwy. Desktop `DesktopBankBudgetSuggestions.find` w propozycjach 1:1 korzysta dodatkowo z terminu ±14 dni, zielony status wymaga unikalnego dopasowania i ±7 dni.

**Skutek:** ten sam wyciąg może pokazać różne propozycje w aplikacjach, nawet jeśli dane są zsynchronizowane. Nie stwierdzono tym audytem błędnego salda Desktopu. Przed deklaracją pełnej zgodności UX obu platform trzeba porównać wyniki na wspólnych danych.

### AUD-FIN-06 — P0 przed wydaniem, niepotwierdzona bramka: fizyczne E2E i migracje

Ostatni Android CI przechodzi testy kontraktów, kompilację, lint i podpis, lecz znaczna część testów finansów to statyczna kontrola kodu albo symulowane scenariusze SQLite/Python. To **nie jest** pełny test Androida, realnych PDF-ów i transakcji przy przerwanym zapisie.

**Bramka:** wszystkie scenariusze testowe poniżej muszą przejść na izolowanych danych przed oznaczeniem 1.0 jako bezpiecznej wersji finansów. Przywracania ZIP nie wykonywać na jedynej instalacji użytkownika.

## Macierz prób po otrzymaniu wyciągów

| Scenariusz | Oczekiwane zachowanie | Priorytet |
| --- | --- | --- |
| Import tej samej historii 2× | identyczne identyfikatory; drugi import nie zmienia salda ani nie dubluje wierszy | P0 |
| Rzeczywista wypłata 4300 zamiast planowanych 4500 | zatwierdzone wpływy +4300; różnica −200 widoczna, bez fikcyjnej wpłaty | P0 |
| Wydatek plan 500, bank 200+300 | po zgodzie dwie rzeczywiste operacje, łącznie −500 raz; plan wykonany | P0 |
| Wydatek plan 500, bank 300 | −300 po potwierdzeniu, 200 pozostałego zobowiązania | P0 |
| Jeden bank 500, dwa plany 200+300 | saldo −500 **raz**, plany rozliczone według podziału | P0 |
| Nadpłata plan 500, bank 550 | −550; nadwyżka 50 nie wraca jako zaległość; możliwość późniejszego odliczenia | P0 |
| Cofnięcie/przerwanie potwierdzenia 1→1 / 2→1 / 1→2 | brak wtórnego księgowania i spójne odzyskanie stanu | P0 |
| Wiele plików częściowo nakładających się datami | wszystkie unikalne transakcje, poprawne liczby i zakres, 0 utraconych dat | P0 |
| 301+ przelewów z parą na końcu | propozycje grupowe także ze starszych stron po poprawce | P1 |
| Import Velo PDF tekstowy + mBank CSV/XLSX | zgodność liczby wpływów i wydatków, sum i dat z oryginałem | P0 |
| Desktop ↔ Android | zgodne dane, bez zdublowanych przelewów, różnice propozycji jawnie oznaczone | P0 do wspólnego Stable |

## Dane oczekiwane od użytkownika

Dołączyć **oryginalne pliki w formatach eksportu**, które powodowały problemy: VeloBank PDF (pełna historia, także miesiące z brakującą datą) oraz mBank CSV/XLSX/PDF zależnie od rzeczywistego eksportu. Dodatkowo okresy z dwoma przelewami na jeden rachunek i zmianą rzeczywistej kwoty. Nie potrzeba haseł, PIN-ów ani loginów. Jeżeli użytkownik chce ograniczyć ujawnienie danych, może zaciemnić numer rachunku i dane identyfikujące osobę w próbce, **nie usuwając** dat, kwot, kierunku, formatów kolumn i stabilnych identyfikatorów pozwalających wykrywać duplikaty.

## Kolejność 17 października

1. Najpierw test i naprawa P0 rozdzielenia SQLite/SharedPreferences dla rozliczeń grupowych, ewentualny recovery/rollback.
2. Test regresyjny dla importu wieloplikowego, pełnych wyciągów i ponownych importów.
3. Usunięcie limitu pierwszych 300 w propozycjach grupowych (z filtrowaniem po miesiącu), test ze starszymi operacjami.
4. Porównanie realnych plików użytkownika z parserami, bez zmiany źródłowych wyciągów.
5. Po faktycznych poprawkach: CI, APK, test i wpis do `docs/EDHOME_STATUS.md`.

**Ważne:** ten dokument jest raportem audytu, a nie deklaracją, że powyższe poprawki zostały już wdrożone.
