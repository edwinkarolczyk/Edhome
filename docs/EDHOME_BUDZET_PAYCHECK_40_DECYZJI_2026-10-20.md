# EDHOME — Budżet, PayCheck i Asystent rozliczania bankowego

**Specyfikacja funkcjonalna i wdrożeniowa • decyzje 1–40 zatwierdzone 20.10.2026**

**Status:** ZATWIERDZONE WYMAGANIA; **BEZ IMPLEMENTACJI**. To dokument projektowy, nie potwierdzenie istnienia funkcji w wydanej APK/EXE. Prace ewentualnie na `beta`, `main`/Stable bez zmian i bez promocji. Przy rozbieżności z historycznym `docs/ROADMAP.md` nowsze zatwierdzone decyzje 1–40 są wiążące dla nowego kreatora; nie znoszą bramek bezpieczeństwa 1.0.

**Powiązane dokumenty:**
- `docs/EDHOME_BUDZET_PAYCHECK_AUDYT_2026-10-20.md` — aktualne problemy P0/P1 i nierozstrzygnięte testy.
- `docs/EDHOME_1_0_PLAN.md` — zamrożony zakres i bramki 1.0.0.
- `docs/ROADMAP.md`, sekcja „EDHOME 1.0 Plus — Budżet miesiąca / PayCheck”.
- `docs/ODBIOR_PAYCHECK_5C_2026-10-08.md` — dwukierunkowy odbiór LAN finansów.

## I. Ustalenia zatwierdzone, dokładnie 40 punktów

### Etap 1 — kreator importu i kwalifikacja przelewów

1. **B — Pytanie po imporcie.** Po imporcie zapytaj „Rozliczyć teraz?”; użytkownik może przejść do kreatora lub zrobić to później.
2. **B — Kolejność propozycji.** Najpierw przelewy najlepiej dopasowane do Budżetu, później niejednoznaczne i pozostałe.
3. **A — Jawne zatwierdzenie.** Nawet idealne dopasowanie jest tylko sugestią; zatwierdzenie przez użytkownika jest konieczne.
4. **C — Brak pozycji Budżetu.** Pokaż dodanie do obecnego miesiąca albo bieżącego i przyszłych miesięcy, oraz „Jednorazowy / Nie dotyczy / Pomiń”; nie zmieniaj salda samym utworzeniem pozycji.
5. **B — Powtarzalność.** Sugeruj cykl po zaobserwowaniu podobnych operacji w co najmniej dwóch miesiącach.
6. **B — Prognozowana kwota.** Dla przyszłych miesięcy przyjmij prognozę, np. 280 zł; rzeczywiste wpływy/wydatki wynikają z kolejnych wyciągów.
7. **C — „Nieistotne”.** Zapytaj, czy tylko ukryć w kolejce kreatora, czy zaksięgować w PayCheck jako operację poza Budżetem. Nie usuwaj dowodu bankowego.
8. **C — Nauka decyzji.** Zapamiętuj wybory; zaproponuj stałą regułę dla odbiorcy, ale utwórz ją dopiero po świadomej zgodzie.

### Etap 2 — przyszłe miesiące i cykle

9. **A — Początek cyklu.** Domyślnie miesiąc zaobserwowanego przelewu; możliwa ręczna zmiana początku.
10. **A — Miesiące wirtualne.** Generuj widok kolejnych miesięcy z reguł; nie materializuj z góry 12 powielonych zapisów.
11. **B — Nowa prognoza.** Przy rachunku 280 zł, faktycznie 340 zł, zaproponuj aktualizację prognozy; decyzja użytkownika.
12. **C — Przewidywana data.** EDHOME sugeruje termin z historii bankowej, **użytkownik zatwierdza**. Nie przyjmuj automatycznie dnia ostatniego przelewu jako wiążącego terminu faktury.
13. **C — Pominięta opłata.** Pokaż: „Pomiń miesiąc bez zaległości” / „Przenieś” / „Pozostaw niezapłacone”.
14. **B — Spłata starszego miesiąca.** Przy późniejszym przelewie pokaż zaległe miesiące i pozwól przypisać go do właściwego okresu.
15. **C — Wcześniejsza spłata rat.** Wykryj prawdopodobną wcześniejszą spłatę i zapytaj o zamknięcie pozostałych rat.
16. **C — Zakres zmian stałej kwoty.** Za każdym razem pytaj: „Tylko ten miesiąc” / „Od tego miesiąca na stałe”.

### Etap 3 — zaawansowane dopasowania, zwroty i transfery

17. **C — Wiele przelewów → jeden rachunek.** Proponuj sumy pełne i niepełne; pozwól ręcznie wybrać dowolne odpowiednie przelewy i wskaż brakującą kwotę.
18. **B — Jeden przelew → wiele rachunków.** Proponuj podział z kwotami przy każdej pozycji; przed rozliczeniem żądaj zatwierdzenia.
19. **B — Niedopłata.** Przy 300 zł płatności do planu 500 zł zapytaj: „Zaległość 200 zł” / „Przenieś” / „Zamknij z powodem”.
20. **B — Nadpłata.** Przy 550 zł do planu 500 zł zapytaj: „Zachowaj 50 zł nadpłaty” / „Przypisz gdzie indziej” / „Bez odliczenia”.
21. **C — Zwrot zakupu.** Zaproponuj powiązanie wpływu zwrotnego z wydatkiem; zachowaj oba dowody, pokaż wydatek 200 zł, zwrot 50 zł i koszt netto 150 zł.
22. **B — Transfer własny.** Rozpoznawaj przelew VeloBank→mBank jako transfer wewnętrzny: zmiana sald kont, zero zmiany łącznego majątku.
23. **C — Alias odbiorcy.** Zaproponuj alias, np. TAURON→Prąd, ale zapisuj regułę dopiero po osobnej zgodzie.
24. **B — Niejednoznaczność.** Wyróżnij najbardziej prawdopodobny wariant na podstawie wcześniejszych zatwierdzonych decyzji; użytkownik zawsze zatwierdza.

### Etap 4 — salda, budżet miesiąca, raty, oszczędności

25. **C — Wiele przekrojów salda.** Łączne saldo, salda rachunków oraz rozróżnienie osoby/finanse wspólne. Nie mieszać własności rachunku z kategorią celu.
26. **C — Pełne, zwijane podsumowanie.** Planowane i faktyczne wpływy/wydatki, salda, zaległości, nadpłaty i prognoza; sekcje można zwijać.
27. **B — Zaległość w kolejnym miesiącu.** Pokazuj jako osobną linię z oznaczeniem miesiąca źródłowego; bez ponownego doliczania do rzeczywistego salda ani do planu pierwotnego.
28. **B — Użycie nadpłaty.** Przy następnym rachunku zaproponuj odliczenie i poproś o zgodę.
29. **B — Cele oszczędnościowe.** Osobna sekcja „planowano odłożyć / faktycznie odłożono”, nie traktuj alokacji jak fikcyjnego wydatku.
30. **B — Nadpłata kredytu.** Zapytaj: „Zmniejsz ratę” / „Skróć liczbę rat” / „Pozostaw jako osobną wpłatę”; nowy harmonogram bankowy musi zostać potwierdzony na podstawie rzeczywistych warunków kredytu.
31. **B — Zamknięcie miesiąca.** Ręczna czynność po pokazaniu nierozliczonych pozycji i decyzji o każdej z nich.
32. **B — Korekta miesiąca.** Ponowne otwarcie z uzasadnieniem i nieusuwalną historią zmian.

### Etap 5 — automatyzacja, cofanie, historia, prywatność i LAN

33. **B — Konfigurowalne przypomnienia.** Osobno dla pozycji/odbiorcy, z rozsądnymi ustawieniami początkowymi.
34. **B — Nierozliczone operacje.** Najwyżej jedno zbiorcze przypomnienie dziennie, z akcją „Kontynuuj rozliczanie”.
35. **C — Podpowiedzi z historii.** Proponuj odbiorcę, kategorię, pozycję, kwotę, termin i cykl; każda zmiana planu lub rozliczenie wymaga zatwierdzenia.
36. **B — Dwie odrębne korekty.** „Zmień przypisanie” i „Cofnij potwierdzenie”; pokaż wpływ na saldo, zachowaj dowód i historię korekty.
37. **B — Pełny audyt.** Zdarzenie zawiera czas, osobę, urządzenie, stan przed/po; wyszukiwanie i eksport historii.
38. **B — Konflikt offline.** Pokaż obie wersje i wymagaj decyzji; zatwierdzonych pieniędzy nie nadpisuj metodą last-write-wins.
39. **C — Prywatne dane.** Wspólne synchronizuj zwyczajnie przez LAN; prywatne tylko po osobnym włączeniu, z kontrolą uprawnień/zakresu.
40. **B — Kontynuacja między urządzeniami.** Po potwierdzonej synchronizacji wznawiaj kreator od nierozliczonych operacji; wcześniej rozliczonych nie pokazuj ponownie.

## II. Inwarianty finansowe (nienegocjowalne)

1. **Budżet to plan** (prognozy, cykle, raty, terminy, alokacje). Sam plan nie zmienia salda.
2. **PayCheck to księga** faktycznie zatwierdzonych operacji pieniężnych. Kwota i data operacji bankowej są zachowywane z dowodu, nawet gdy pozycję przypisano do innego miesiąca budżetowego.
3. **Import to dowód, a nie potwierdzenie.** Po imporcie tylko propozycje; brak samoczynnego księgowania nawet przy wysokiej pewności. To *zmiana wymagania* względem istniejącego `autoSettleImportedBankEvidence` w bieżącej Becie i wymaga etapu P0.
4. **Unikalność:** ten sam dowód bankowy nie może wpłynąć dwukrotnie na księgę; wielokrotny import jest idempotentny. Dopuszczalna alokacja jednej operacji do wielu rachunków musi sumować się do maksymalnie jej rzeczywistej kwoty.
5. **Przelew rozdzielony na dwie pozycje** 500 zł→200+300 zł jest nadal jedną rzeczywistą operacją `−500 zł`, nie dwoma przelewami `−500 zł`. Przy 200+300→500 zł zachowujemy dwie rzeczywiste operacje.
6. **Zaległość** jest roszczeniem/pozostałym planem, a nie kolejną obciążającą transakcją bankową. **Nadpłata** jest kredytem do późniejszego uznania, nie generuje samoczynnie dodatniego przelewu; kwota odliczeń nie przekracza dostępnego kredytu.
7. **Nadwyżka ceny vs nadpłata:** jeżeli bank pobrał 550 zł przy szacowanej cenie 500 zł, bez zgody nie zakładamy, że 50 zł jest uznaną przez odbiorcę nadpłatą przyszłych rachunków. „Bez odliczenia” zostawia 550 zł rzeczywistego wydatku i jedynie różnicę plan/fakt; kredyt powstaje wyłącznie po świadomej kwalifikacji.
8. **Zwrot 50 zł** jest osobną potwierdzoną operacją dodatnią, powiązaną z zakupem 200 zł; net spending = 150 zł, historia obu płatności nie znika.
9. **Transfer własny** to dwie strony przepływu kont albo logiczne powiązanie z kontrolą dostępnych dowodów, a nie dochód/wydatek netto gospodarstwa; brak drugiej strony wymaga jawnego statusu do uzgodnienia, bez tworzenia fałszywego dokumentu bankowego.
10. **Prywatność:** konto prywatne, wydatki prywatne i przypisany domownik podlegają osobnym uprawnieniom; współdzielony Desktop nie uzyskuje dostępu do nich tylko przez zeskanowanie QR wspólnego LAN.
11. **Korekta nie kasuje historii.** Cofnięcie potwierdzenia tworzy zdarzenie kompensujące i ponownie umożliwia bezpieczne rozpatrzenie dowodu; „Zmień przypisanie” nie zmienia rzeczywistej kwoty ani salda konta.
12. **Brak cichego resetu przy ZIP, aktualizacji i konflikcie**; w razie nierozstrzygniętej awarii finansowej blokuj podejrzane rozliczenie i wymagaj naprawy/kopii.

## III. Architektura funkcjonalna i przepływy

### 1. Jedna księga; trzy osobne warstwy

```text
Wyciągi PDF / CSV / XLSX (lokalnie; VeloBank, mBank)
                 │
                 ▼
DOWODY BANKOWE (unikalne ID, daty, kwoty, znaki, źródło)
                 │
                 ▼
ASYSTENT ROZLICZANIA  ── sugestie, historia decyzji, postęp
                 │                  ▲
                 │                  │
                 ├───────────── BUDŻET (reguły, plan,
                 │             przyszłe miesiące, zaległości,
                 │             raty, cele i odbiorcy)
                 ▼
     JAWNE ZATWIERDZENIE UŻYTKOWNIKA
                 │
                 ▼
PAYCHECK (jedna faktyczna księga, konta, saldo, zwroty,
          alokacje, transfery, dziennik korekt)
                 │
                 ▼
             LAN / ZIP
                 │
          Android ↔ Desktop
```

Kreator **nie może** przechowywać drugiej księgi salda. Zwraca instrukcję świadomie wybranej operacji do istniejącej księgi PayCheck i planu. Reguły oraz stan postępu są danymi pomocniczymi. Importowane operacje i oryginalne pliki/dowody należy zachować bez nadpisania.

### 2. Sugerowane byty domenowe (nazwy robocze, nie nakaz nowej bazy)

- `BankEvidence`: ID dowodu, źródło, nazwa banku, identyfikator konta (jeśli znany), kierunek, kwota w groszach, data księgowania, kontrahent, opis, status rozliczenia.
- `BudgetRule`: pozycja stała/jednorazowa, właściciel/zakres wspólny, odbiorca, cykl, aktywność, kwota prognozowana, zatwierdzony termin i aktualizacje.
- `BudgetOccurrence`: wirtualny widok miesiąca, z materializacją jedynie wyjątków/korekt i zdarzeń płatności. Stare miesiące nie mogą się zmieniać po edycji nowej prognozy.
- `PaycheckTransaction`: niepowtarzalna operacja faktycznie zatwierdzona, powiązana z dowodem, kontem i zakresem prywatnym/wspólnym.
- `SettlementAllocation`: jedna lub wiele relacji kwotowych transakcja↔pozycja/miesiąc; suma alokacji nie może przekroczyć faktycznie zaksięgowanej kwoty.
- `Carryover`/`Credit`: niedopłaty i nadpłaty identyfikowane według rachunku i miesiąca źródłowego, z historią wykorzystania.
- `RefundLink` i `InternalTransferLink`: powiązania źródłowych operacji bez ich przepisywania; transfer wewnętrzny nie zniekształca przychodu i kosztu.
- `MatchSuggestion`/`LearningRule`: lokalne sugestie z poziomem pewności, wyjaśnieniem i zgodą na utrwalenie aliasu/reguły.
- `WizardReviewState`: ukończone/pominięte/ukryte/dalsze rozliczanie, zapisane według stabilnych identyfikatorów, nie według indeksu listy. Potwierdzona operacja zawsze znika z kolejki niezależnie od przesłanego wskaźnika.
- `FinancialAuditEvent`: append-only kto, kiedy, gdzie, przed/po, intencja, UUID/łańcuch zdarzeń; rozstrzyganie konfliktów.
- `AccountScope`/`PrivateSharingConsent`: zakres rachunku, właściciel i jawna, możliwa do cofnięcia zgoda na synchronizację prywatną.

**Migracja:** zachować `paycheck_transactions`, `bank_evidence_queue`, `PaycheckMonthlyBudget` i tabele `budget_*` jako istniejącą bazę kompatybilności. Najpierw audyt mapowania, wersjonowana migracja i testy round-trip, bez zastąpienia starego JSON/SQLite nowym schematem w jednej nieodwracalnej operacji. Szczególnej ochrony wymaga dziennik `bank_group_settlement_recovery`, dziś poza whitelistą `DataBackup.TABLES`.

### 3. Kreator — sekwencja i nawigacja

1. **Import:** parser podaje liczbę plików, unikalnych operacji, duplikatów, zakres dat, sumę wpływów/wydatków i liczbę wierszy odrzuconych z przyczyn jawnych. Jeżeli parser nie gwarantuje kompletności, raportuje brak możliwości bezpiecznego importu, nie uzupełnia fikcyjnych przelewów.
2. **Pytanie:** „Zaimportowano N nowych przelewów. Rozliczyć teraz?” → „Rozlicz teraz” / „Później”. Brak potwierdzenia importu jako rozliczenia.
3. **Lista robocza:** sortowanie według jakości sugestii (wysoka, do sprawdzenia, brak), chronologiczny porządek w obrębie grup, filtrowanie miesiąca i banku. Wszystkie strony historii są osiągalne.
4. **Karta jednej operacji:** bank, konto, data, pełny opis/kontrahent, kwota, kierunek i proponowane pozycje Budżetu. Zawsze widoczna planowana kwota, faktyczna kwota i różnica; kolor+tekst, nie sam kolor.
5. **Działania:** „Zatwierdź dopasowanie”, „Wybierz inne”, „Połącz przelewy”, „Podziel przelew”, „Utwórz pozycję w Budżecie” (ten/od tego miesiąca; jednorazowo/cyklicznie), „Nie dotyczy Budżetu” (ukryj albo zatwierdź poza planem), „Pomiń na później”.
6. **Ekran zatwierdzenia:** przed/po salda, wybrane rachunki i miesiące, odchylenia, wykorzystany kredyt, konsekwencje dla rat/zaległości; przy zgodzie dopiero zapis i trwały log.
7. **Podsumowanie:** rozliczono, pominięto, ukryto, wymaga decyzji, brak dopasowania, importowane duplikaty; przyciski „Kontynuuj”/„Zakończ”. Ukrycie z kolejki nie oznacza skasowania dowodu.
8. **Wznowienie:** zakładka Budżet i historia bankowa PayCheck prowadzą do tego samego kreatora. Po LAN użyj stanu transakcji i potwierdzonych identyfikatorów, a nie ślepo zsynchronizowanego numeru kroku. Brak dostępu LAN = praca offline, a później kontrola konfliktu.

### 4. Automatyzacja i przyszłe miesiące

- Przy dwóch podobnych operacjach w odrębnych miesiącach sugeruj cykl i alias; bez zgody nie dodawaj reguły.
- Po rozpoznaniu zapisanego odbiorcy proponuj pozycję, kategorię, datę, kwotę i ewentualną zmianę prognozy; **niczego samoczynnie nie księguj**.
- Terminu z banku użyj jako sygnału do przewidywania, a nie twardego terminu płatności; zatwierdzony termin faktury ma pierwszeństwo. Odrębnie data księgowania i data, do której rachunek jest płatny.
- Reguły miesięczne/kwartalne/roczne generują przyszłe okresy w widoku; wyjątki dla miesiąca są osobnym nadpisaniem. Zmiana od wybranego miesiąca nie przepisuje historii.
- Zaległość zachowuje miesiąc źródłowy. Płatność w listopadzie może spłacić rachunek październikowy, ale listopadowa data banku nie jest fałszowana.
- Przypomnienia domyślnie umiarkowane i edytowalne per rachunek lub odbiorca; respektują ciszę nocną, uprawnienia Androida i ustawienia użytkownika. Jedno zbiorcze przypomnienie nierozliczonych dziennie; licznik aktualizowany po zatwierdzeniu/ukryciu.
- Zamknięcie miesiąca jest świadomą decyzją; ponowne otwarcie wymaga powodu i zapisuje ślad audytowy.

### 5. Zgodność prywatna, kopie, synchronizacja

- `shared` i `private` to autoryzowane zakresy danych w **jednym produkcie PayCheck**, nie niezależne, przypadkowo synchronizowane księgi.
- Wspólny Budżet synchronizuje się tylko w LAN, bez chmury. Prywatne dane włączane świadomie **per użytkownik / urządzenie / zakres** i chronione dostępnymi mechanizmami szyfrowania/sejfu; domyślnie nieudostępniane na wspólnym Desktopie.
- Identyfikatory UUID, znaczniki wersji i zdarzenia audytowe pomagają odróżnić retry od innej zmiany. Konflikt dwóch edycji tej samej pozycji lub alokacji blokuje nadpisanie i pokazuje obie wersje.
- Kopia pełna i selektywna ZIP musi zachować definicje, wirtualne cykle z wyjątkami, realne potwierdzenia, historię, reguły, aliasy, kolejkę kreatora i uprawnienia z poszanowaniem prywatności; starsze kopie nadal muszą się odtworzyć. „Prywatne” nie może być włączane do wspólnego eksportu bez jawnej zgody i zabezpieczenia.
- Aktualizacja nie zmienia wybranego motywu, kont ani ustawień, nie wymaga odinstalowania telefonu.

## IV. Ekrany docelowe

### Android

- **Budżet miesiąca:** polskie nazwy dni i miesięcy, ‹ poprzedni / następny ›, zwijane sekcje wpływów, rachunków, rat, oszczędności, zobowiązań; każda pozycja jedną zwartą linią i jedna rozwinięta naraz. Suma „plan / faktycznie / pozostało” i salda kont; statusy zielony=potwierdzony, żółty=częściowy/wymaga decyzji, czerwony=nieopłacony, niebieski=przeniesiony wraz z ikoną/tekstem. Przycisk „Rozlicz nierozpoznane przelewy”.
- **PayCheck:** konta i salda, potwierdzone operacje vs `pending`, pełna historia bankowa z filtrami, import plików, „Kontynuuj rozliczanie” i historia korekt.
- **Asystent:** lista postępu N z M, karta jednej operacji, panel szczegółów proponowanej pozycji, akcje, potwierdzenie salda, lista zakończenia; na małym ekranie elementy z pełnymi polskimi podpisami bez ucięcia.
- **Ustawienia:** terminy/przypomnienia per odbiorca, aliasy i reguły uczenia, prywatność i uprawnienia sync, eksport audytu, status LAN i blokady konfliktów.

### Desktop (Windows)

- Te same funkcje i reguły księgowania; rozkład dwukolumnowy: **po lewej** lista nierozliczonych operacji z filtrem miesiąca/banku i statusem, **po prawej** szczegóły, propozycje i działania z potwierdzeniem.
- Pełny CRUD Budżetu (nie tylko nazwa i kwota miesiąca), zapisywanie offline i porównanie konfliktów przed synchronizacją. Daty przez czytelny kalendarz, kwoty z formatem PLN i rozdzieleniem plan/fakt.
- Żaden etap nie może polegać na staroci `paycheck-budget-plan.json` jako drugiej księdze. Interakcje Windows i Android dzielą scenariusze akceptacyjne.
- Kiedy PC nieosiągalny, Android pozostaje w pełni funkcjonalny bez wymuszania parowania; Desktop pokazuje ostatni potwierdzony stan sync, nie mylące „połączono” po samym QR.

## V. Podział wdrożenia: 1.0 vs 1.0 Plus

**Decyzje 1–40 są docelowym kontraktem, nie obietnicą wdrożenia wszystkich przed 25.10.2026.** Istniejąca bramka wydania `docs/EDHOME_1_0_PLAN.md` ma pierwszeństwo: bezpieczeństwo P0, zero utraty danych i zweryfikowane saldo.

| EDHOME **1.0 — obowiązkowa stabilizacja i minimalny kreator** | EDHOME **1.0 Plus — pełny zakres 40 decyzji** |
|---|---|
| Usunięcie P0: niekontrolowane auto-księgowanie po imporcie, dziennik rozliczeń ↔ ZIP/restore, błędy potwierdzenia, niedopłaty/nadpłaty i duplikaty | Inteligentne przewidywanie przyszłych terminów/kwot/cykli, wykrywanie powtarzalności i jawne aliasy/reguły |
| Zgodne i ręcznie zatwierdzane 1:1, 2→1, 1→2; historia importu i wznowienie po awarii; usunięcie blokujących limitów 300/500 | Dowolne grupy częściowe i ich korekty, zaawansowane rozdzielanie, powiązane zwroty i transfery między kontami |
| Minimalny krokowy interfejs „Rozliczyć teraz?” → propozycja/wybór/zatwierdzenie/pomiń, bez przenoszenia księgowania do nowej księgi; kontynuacja lokalna | Pełne wieloetapowe UX, automatyzacja przypomnień, szczegółowa historia „przed/po”, kontynuacja między urządzeniami |
| Budżet→PayCheck `pending`, saldo dopiero po zatwierdzeniu; dodanie jedno-/cyklicznego planu na bazie dowodu, gdzie bezpieczne | Rozbudowany widok miesięczny wg arkusza, cele oszczędnościowe, konta i właściciele, pełne podsumowania i kredyty z nowym harmonogramem |
| Kompletny backup ZIP/restore i minimum parytetu finansowego Android/Desktop z **testem na sprzęcie**, stabilne offline | Pełny CRUD, uprawnienia prywatnych danych i opt-in synchronizacji, konflikty finansowe z audytem zmian i porównywaniem wersji |

**Warunek minimalistycznego kreatora 1.0:** może wejść do 1.0 wyłącznie jeśli jest w pełni bezpieczny, przetestowany i mieści się w zamrożonym P0. W przeciwnym razie 1.0 utrzymuje poprawne ręczne ścieżki i jasno przesuwa nowy UI do Plus, bez ogłaszania go jako zrobionego. Jest to priorytet ochrony danych, nie wycofanie zatwierdzonych decyzji.

**Plan Plus A–D** zgodny z istniejącą roadmapą:
- **Plus-A — Miesięczny Budżet i UX:** zwarta lista, rozbudowane statystyki, reguły wirtualnych miesięcy, plan/fakt, zamykanie/otwieranie.
- **Plus-B — Kreator i automatyzacja:** proaktywne propozycje, częściowe grupy, przewidywanie terminów i kwot, aliasy i powiadomienia, pamięć postępu.
- **Plus-C — Zaawansowane finanse i prywatność:** transfery własne, zwroty, konta, oszczędności, kredyty, prywatne dane z kontrolą dostępu.
- **Plus-D — Weryfikacja i wydanie:** regresje wszystkich 40 decyzji, audyt korekt, wieloletnia historia, ZIP i LAN dwukierunkowy, test fizyczny Android/Windows.

## VI. Kolejność prac Android + Desktop (gdy użytkownik osobno zgodzi się na implementację)

**Brama 0 — nie zmieniać nic w Stable.** Backup kodu i testowych danych, zdefiniowany zestaw transakcji i identyfikatorów oraz mapowanie obecnych tabel. Nie wykonywać prób na jedynej prywatnej księdze użytkownika.

1. **P0 księgowość i restore:** odtwórz i zabezpiecz: `BankBudgetGroupSettlement` journal poza ZIP, naprawę 1:1 ograniczoną do 500 ostatnich, propozycje ucięte do 300, nadpłatę wracającą jako zaległość, pomijane miesiące historii, ryzyko auto-rozliczania CSV/mBank bez zgody. Test crash w każdym kroku, odtwarzanie z kopii i ponowny import.
2. **Model decyzji i akceptacji:** stabilne UUID dowodu, powiązań i zdarzeń; atomowe/odzyskiwalne zapisy księgi i planu; reguły per miesiąc/kierunek i pełne kwoty PLN w groszach. Wspólny kontrakt testowy dla Android i Desktop.
3. **Android MVP kreatora:** wejście po imporcie, lista kwalifikowanych dowodów, ręczna akceptacja 1:1, 2→1, 1→2, „Dodaj do Budżetu”, „Pomiń” i „Nie dotyczy”; podgląd przed/po i brak salda z samej propozycji. Zapis progresu offline.
4. **Desktop MVP i LAN:** odpowiednik na Windows w jednej księdze, pełna ścieżka import→propozycje→zatwierdzenie; symetryczna synchronizacja. Weryfikacja różnic zamiast cichego nadpisania.
5. **Plus-A–C:** odrębne migracje i wydania funkcji inteligentnych, oszczędności, kredytów, kont i prywatności. Nie zmieniać salda przy migracji definicji Budżetu.
6. **Testy i wydanie:** CI obydwu platform, różnica salda 0 gr na scenariuszach, podpisana aktualizacja Android bez odinstalowania, kompletne stare/nowe ZIP na odizolowanych danych, rzeczywiste urządzenia LAN i raport GO/NO-GO.

### Krytyczne testy odbiorcze

| ID | Test | Oczekiwany wynik |
|---|---|---|
| A01 | Dodaj w Budżecie plan 500 zł | PayCheck `pending`, saldo nie zmienia się |
| A02 | Bank 300 zł do planu 500 zł | −300 zł po zgodzie; jawna decyzja co z 200 zł |
| A03 | Bank 200+300 do planu 500 zł | Dwa dowody, razem −500 zł, jedna rozliczona pozycja |
| A04 | Bank 500 do planów 200+300 | Jeden dowód, −500 zł tylko raz, dwa pokryte plany |
| A05 | Plan 4500 zł, bank 4300 zł | +4300 zł tylko po zgodzie; różnica −200 zł |
| A06 | Plan 500 zł, bank 550 zł | −550 zł; 50 zł jako kredyt wyłącznie po wyraźnej kwalifikacji |
| A07 | Zwrot 50 zł do zakupu 200 zł | Dwa dowody, koszt netto 150 zł, saldo zgodne z bankiem |
| A08 | Transfer 1000 zł VeloBank→mBank | Salda per konto −1000/+1000, łączny przepływ własny 0 |
| A09 | Import identycznego pliku 2× / plików z datami nakładającymi się | Wszystkie unikalne wiersze raz, 0 zł podwójnych potwierdzeń |
| A10 | 301+ otwartych / 500+ potwierdzonych operacji | Starsze sugestie i naprawy pozostają osiągalne |
| A11 | Przerwij księgowanie, restart, potem ZIP→restore | Żadnego dubletu salda; kompletna spójność `journal`, historii i planu |
| A12 | Październikowa zaległość, przelew w listopadzie | Termin bankowy listopad, rozliczenie wskazanego miesiąca, bez duplikatu długu |
| A13 | Zmień prognozę od grudnia | Dawniejsze miesiące niezmienione; bez wpisów bankowych na przyszłość |
| A14 | Przewidywany termin | Propozycja bez zapisanej zmiany przed kliknięciem „Zatwierdź” (decyzja 12=C) |
| A15 | Cofnij i zmień przypisanie | Audyt przed/po, bez niszczenia dowodu, saldo spójne z wybraną korektą |
| A16 | Konflikt Android/PC offline | Dwustronny podgląd konfliktu, brak cichego nadpisania i podwójnego księgowania |
| A17 | Prywatne konto na wspólnym PC | Brak odczytu/synchronizacji bez jawnej i ważnej zgody |
| A18 | Przerwij kreator po 8 z 40, uruchom na PC | Po potwierdzonym LAN podgląd tylko nadal wymagających rozpatrzenia |
| A19 | Tekstowe PDF VeloBank, CSV/XLSX mBank | Kontrola liczby operacji, dat oraz sum obu kierunków względem źródła |
| A20 | Zamknięcie i ponowne otwarcie miesiąca | Ostrzeżenia, uzasadnienie i historia bez przeliczania operacji podwójnie |

**Odbiór finansów 1.0:** zero otwartych P0, prawdziwe źródła bankowe zweryfikowane, saldo/UUID zgodne co do grosza, pełny i selektywny ZIP, restart i LAN Android↔Desktop. **Brak wyników prób na sprzęcie = brak podstaw do zadeklarowania ukończenia.**

## VII. Status końcowy tej pracy

- Decyzje **1–40**: zatwierdzone w rozmowie.
- Architektura, UX, plan wdrożenia i kryteria odbioru: opisane.
- Kod Android/Desktop, migracje, CI, nowe APK/EXE: **NIE ZMIENIANO**.
- `main` Stable: **bez zmian**.
- Wdrożenie wymaga osobnej zgody użytkownika.
