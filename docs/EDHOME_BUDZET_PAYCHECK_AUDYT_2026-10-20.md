# EDHOME — audyt Budżet / PayCheck / bank / Desktop (20.10.2026)

**Gałąź:** `beta`. **Android:** kod 0.8.1.136 / 329. **Zakres:** statyczna analiza bieżących źródeł, testów kontraktowych, dokumentacji i CI GitHub. **Nie wykonano** testu fizycznego, prawdziwego importu wyciągu ani testu LAN E2E. **Werdykt: NO-GO dla ogłoszenia finansów 1.0 jako odebranych.** `main` Stable bez zmian.

## Potwierdzone ograniczenia kodu i bramki

| ID | Priorytet | Stan | Dowód w beta | Skutek i działanie |
|---|---|---|---|---|
| FIN-20-01 | **P0** | Luka zabezpieczenia przy rozliczeniu w trakcie | `BankBudgetGroupSettlement.JOURNAL` = `bank_group_settlement_recovery`; brak tej tabeli w `DataBackup.TABLES`, a `DataBackup.exportJson` nie sprawdza i nie dokańcza dziennika | Nowa naprawa awarii SQL→SharedPreferences istnieje, ale backup/restore wykonane pomiędzy krokami wymagają jawnej blokady albo bezpiecznej normalizacji. Nie wystarczy po prostu kopiować tabeli dziennika do ZIP; test crash→ZIP→restore→saldo i ponownego uruchomienia. |
| FIN-20-02 | **P1** | Potwierdzony limit wyszukiwania grup | `tests/check_budget_bank_group_android.py` wymaga w kodzie `MainActivity.showBankBudgetProposals` wartości `Math.min(total,300)`; `appendBankBudgetGroupProposals` ma limit 40 sugestii | Otwarta para 200+300 może istnieć w historii, ale nie zostać zasugerowana. Wprowadzić wyszukiwanie po miesiącu i paginację wszystkich kandydatów, bez blokady UI; test 301+ pozycji. |
| FIN-20-03 | **P1** | Potwierdzony limit naprawy 1:1 | `BankBudgetSingleSettlement.repairUnlinked`: `ORDER BY t.id DESC LIMIT 500` | Starsze nierozwiązane powiązanie PayCheck→Budżet może nie zostać naprawione po awarii, gdy przybyło >=500 nowszych potwierdzeń. Wykrywać i naprawiać wszystkie nierozwiązane wpisy partiami. |
| FIN-20-04 | **P1** | Nierówne reguły Android/Desktop | `DesktopBankBudgetSuggestions.find`: termin do 14 dni, zielony tylko unikatowa zgodna para z nazwą i datą do 7 dni; Android `BankBudgetMatchRules` i `autoSettleImportedBankEvidence` używają innych kryteriów | Ten sam wyciąg może generować inne propozycje na dwóch urządzeniach. Jeden zestaw scenariuszy/fixture dla obu platform. |
| FIN-20-05 | **P1** | Niepełna edycja na Desktop | `DesktopBudgetMirror.showBudget`: tworzenie pozycji obejmuje typ/cykl/odbiorcę/raty, ale `Edytuj pozycję` pozwala tylko na nazwę i kwotę jednego miesiąca | Niepełny CRUD względem Androida. Dodać edycję cyklu, terminu, odbiorcy, rat i zakończenia pozycji z zachowaniem historii i synchronizacji. |
| FIN-20-06 | **P0 – bramka odbioru** | Brak rzeczywistego testu dwukierunkowego LAN | `docs/ODBIOR_PAYCHECK_5C_2026-10-08.md` A1–A20 nieodebrane; `docs/EDHOME_STATUS.md` 19.10 potwierdza łączność Hub/logów, lecz nie nową pełną zgodną synchronizację baz | Porównać UUID, sumy, zaległości i nadpłaty na telefonie/PC w obu kierunkach, przy zerwanym połączeniu i konflikcie. Nie nadpisywać automatycznie żadnej bazy. |
| FIN-20-07 | **P0 – bramka odbioru** | Nieudokumentowany E2E rzeczywistych banków i sald | Testy `tests/check_budget_bank_automatch_android.py`, `check_bank_group_recovery_p0.py`, `check_paycheck_budget_finance_regression.py` obejmują głównie kontrakty kodu i symulacje SQLite, nie Android + prawdziwy VeloBank/mBank | Odbiór po próbach 1:1, 2→1, 1→2, 4500→4300, częściowych wpłat, nadpłaty wracającej jako zaległość, duplikatu importu, restartu, przerwania transakcji, powrotu z ZIP. |

## Specyfika importów — nie mylić z utratą danych

- **VeloBank PDF:** `BankStatementVeloPdf` czyta tekstowy PDF, ale nie skan obrazu. Import może zostać odrzucony przy niejednoznacznych/nierozpoznanych wierszach. Kod `autoSettleImportedBankEvidence` wyklucza `velo_pdf` z automatycznego rozliczania: ręczny wybór jest świadomym bezpiecznikiem, nie dowodem usterki.
- **mBank:** `BankStatementMbank` wymaga charakterystycznych nagłówków. Trzeba potwierdzić rzeczywistą liczbę przelewów, wpływy/wydatki, salda i zakres dat na oryginalnych ekspor­tach.
- **CSV:** parser wymaga stabilnego ID i określonego schematu. Bez próbki bankowej nie da się stwierdzić, czy brakujące dni w historii zostały pominięte przez parser czy przez prezentację.
- **Uwaga do zasad użytkownika:** Android po imporcie CSV/mBank może wykonać `autoSettleImportedBankEvidence` dla silnych, jednoznacznych par (`BankEvidenceStore.match` i `PaycheckMonthlyBudget.match`); propozycje niejednoznaczne i Velo PDF wymagają ręcznej akceptacji. Przed produkcyjnym 1.0 sprawdzić, czy automat nie księguje przelewu bez oczekiwanego potwierdzenia użytkownika. Funkcja `pending` utworzona z planu sama nie zmienia salda.

## Mechanizmy obecne w kodzie (nie mylić z odbiorem E2E)

- Budżet tworzy oczekujące `pending`, a nie potwierdzone saldo.
- `BankEvidenceStore` przechowuje unikalne dowody; historia ma stronicowanie SQLite i możliwość odrzucania niepowiązanych importów.
- `BankBudgetSingleSettlement.confirm` i `repairUnlinked`, `BankBudgetGroupSettlement` z dziennikiem recovery oraz reguły 2→1/1→2.
- Rejestr historii, nadpłat/niedopłat, reguł alokacji i podziałów; poprawki pól kalendarza na Androidzie mają kontrakty CI.
- Desktop pozwala dodawać pozycje wspólnego Budżetu; podgląd sugestii `DesktopBankBudgetSuggestions` jest jawnie **tylko do odczytu**, bez rozliczania salda.

## Wskazania użytkownika wymagające odbioru na urządzeniu (bez twierdzenia, że obecnie występuje błąd)

1. Niedziałające potwierdzenie przelewu po kliknięciu; porównać komunikat sukcesu, status dowodu, wpis historii i saldo przed/po.
2. Nadpłata wracająca jako zaległość w następnym miesiącu.
3. Znikające daty/dni z importowanego wyciągu, pełny reimport i brak podwójnych rekordów.
4. Pulsujące dopasowania po imporcie, w tym 200+300=500 i 500→200+300; ręczne zatwierdzenie.
5. Pięć pól kalendarza na Androidzie: kod został dopisany, ale brak testu realnego dotyku.
6. Zgodność budżetu, rat, nadpłat i historii po LAN Desktop↔Android.

## Kolejność napraw oraz kryteria odbioru

1. **P0 odzyskiwanie/ZIP:** wykrycie nieukończonego dziennika, bezpieczna blokada eksportu/restore lub jego zakończenie i weryfikacja; testy awaryjne i selektywny ZIP. Saldo/zdarzenia bez zdublowania.
2. **P0 odtworzenie realnych banków:** fixture anonimizowane VeloBank/mBank; suma operacji, liczba wierszy, brakujące daty, powtórny import, potwierdzenia, częściowe i nadpłaty. Różnica salda względem zweryfikowanej sumy = 0 gr.
3. **P1 brak cichych limitów:** usunąć cięcie 300 sugestii i 500 napraw, zoptymalizować indeksy/stronicowanie, testować >3001 dowodów.
4. **Desktop:** ujednolicić reguły sugestii i operacje finansowe; pełny CRUD oraz testy LAN w obie strony z rozłączeniem i konfliktem.
5. **Go/No-Go:** dopiero po CI Android+Desktop, podpisanym wydaniu, testach z rzeczywistymi urządzeniami i sprawdzonym backup→restore. `main` bez automatycznej promocji.

**Nie implementowano poprawek w ramach tego audytu.** Rozbudowany Budżet **1.0 Plus** (arkusz: opłaty, raty, oszczędności, rozrachunki i podsumowania plan/fakt) jest zadaniem roadmapy *po* stabilizacji P0 1.0, nie aktualnie wdrożoną funkcją.
