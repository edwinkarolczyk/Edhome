# EDHOME — aktualny stan prac

> **Stały dziennik kontynuacji projektu.** Przed rozpoczęciem pracy nad EDHOME przeczytaj najpierw ten plik, a po zakończeniu każdego etapu zaktualizuj go w tym samym repozytorium. Szczegółowe wymagania pozostają w [ROADMAP.md](ROADMAP.md); ten plik jest krótkim, aktualnym punktem wznowienia pracy.

## Metryka

| Pole | Stan |
|---|---|
| Ostatnia aktualizacja | 2026-10-08 — etap 5C, pierwsza seria wdrożeń (stan przed odbiorem) |
| Repozytorium | `edwinkarolczyk/Edhome` |
| Gałąź robocza | `beta` |
| Stable | `main` — **zakaz zmian, merge i publikowania nowego Stable bez osobnej, wyraźnej akceptacji Edwina** |
| Android Beta | **0.8.0.56 / versionCode 248** — w kodzie `app/build.gradle`; CI dla ostatniego HEAD w toku, nie mylić z opublikowanym manifestem |
| Desktop Beta | **0.7.0.108** — w kodzie, Gradle i workflow na `beta`; CI i instalator z ostatniego HEAD nieodebrane |
| Ostatni odczytany HEAD `beta` przed utworzeniem tego pliku | `85fec69ff4a6c154d90bd9e9e0a625e20a9bb2d0` — commit wyłącznie roadmapy |
| Ostatni zweryfikowany CI Android | [run #37764090792](https://github.com/edwinkarolczyk/Edhome/actions/runs/37764090792) — **success**, commit `58bb0d4c9228faa78011ab6afcda509e07750198` |
| Ostatni zweryfikowany CI Desktop | [run #37764090766](https://github.com/edwinkarolczyk/Edhome/actions/runs/37764090766) — **success**, commit `58bb0d4c9228faa78011ab6afcda509e07750198` |
| Bieżący etap | **PayCheck / Budżet miesiąca — etap 5C (otwarty)** |
| Następny krok | 5C: odebrać buildy 0.8.0.56/248 i 0.7.0.108, przetestować edycję PC↔Android, konflikty i backup; następnie dopracować delta zmian Budżetu oraz edycję wpłat/nadpłat |

**Ważne:** zielone CI dotyczy wskazanego commita, a nie automatycznie wszystkich przyszłych zmian. Wydania i funkcje wymagające testów na fizycznych urządzeniach są oznaczane jako *nieodebrane*, dopóki taki test faktycznie nie przejdzie.

## Aktywny temat 1: PayCheck — Budżet miesiąca

**Kontrakt produktu:** [ROADMAP.md — Budżet miesiąca 1.0, 25/25](ROADMAP.md#paycheck--budżet-miesiąca-10-kontrakt-odbioru-2525). Stan techniczny nie jest równoznaczny z odbiorem wszystkich 25 warunków.

| Część | Status | Potwierdzony zakres / brak |
|---|---|---|
| Etapy 0–4 | Wykonane kodowo według roadmapy; odbiór całości otwarty | Reguły budżetu i zachowania UI; nadal wymagane testy fizyczne i regresyjne |
| **5A — Android / trwałość** | **Kod wdrożony, CI success** | Zgodnie z roadmapą: SQLite v46, tabele `budget_items`, `budget_occurrences`, `budget_recipients`, `budget_credits`, `budget_history`; historia bez limitu 6000 wpisów w archiwum; migracja, backup i protokół `sync-records` |
| **5B — Desktop / odczyt** | **Kod wdrożony, CI success** | Podgląd wspólnego Budżetu z danych Androida; miesiące, faktury i korekty. Stary `paycheck-budget-plan.json` zachowany, **brak pełnej edycji wspólnego Budżetu z PC** |
| **5C — synchronizacja i konflikty** | **W REALIZACJI / P0** | Kod: ochrona append-only historii na Androidzie i Desktopie, kopia ZIP przed „Desktop wygrywa”, Desktop: edycja istniejącej pozycji (nazwa/kwota miesiąca) i zapis do tych samych struktur. Brak odbioru fizycznego, pełnych per-rekordowych delt ustawień Budżetu, edycji wpłat/nadpłat oraz automatycznego scalania konfliktowych zmian finansowych. |
| Etap 6 — ergonomia i odbiór | **OTWARTE** | Zwarty nagłówek, odbiorcy, pełna regresja 25/25 i fizyczny test obu urządzeń |

### 5C — faktycznie wykonane w kodzie (nie mylić z odbiorem)

- **Android:** `DataBackup.restoreJson` przed kasowaniem tabel przechowuje lokalną historię SQL i cache, po imporcie scala zdarzenia append-only w tej samej transakcji. Niezgodny UUID → wyjątek i rollback; po scaleniu odtwarzane metadane `sync_records`. `SyncRecordStore.applyPatch` zabrania usuwania i aktualizacji istniejących zdarzeń `budget_history`.
- **Android / konflikt:** wybranie „Desktop wygrywa” najpierw zapisuje lokalne archiwum ZIP do wewnętrznego katalogu `hub-conflict-backups`; brak udanej kopii przerywa zastąpienie danych. Eksport i przywrócenie tej wewnętrznej kopii z poziomu UI wciąż wymaga osobnego odbioru.
- **Desktop Hub:** przy pełnym snapshot zachowuje poprzednie zdarzenia historii, nie nadpisuje różnej treści tego samego UUID, odrzuca destrukcyjne patche historii również w trybie „Telefon wygrywa”.
- **Desktop PayCheck:** istniejącą pozycję wspólnego Budżetu można edytować z PC (nazwa i planowana kwota pojedynczego miesiąca). Jedna kopia snapshotu aktualizuje legacy JSON ustawień, `budget_items`, `budget_occurrences` i dopisuje `budget_history`. Starszy `paycheck-budget-plan.json` pozostaje nietknięty.
- **Testy:** nowe JUnit `DesktopSharedBudgetEditsTest`, `DesktopBudgetHistoryArchiveTest`; źródłowy test `check_paycheck_budget_stage5c_guard.py`; Windows i Android CI uruchomione dla nowych zmian. **Nie oznaczaj 5C jako zakończonego bez wyników końcowego CI i testu dwóch fizycznych urządzeń.**
- **Wersje robocze:** Android 0.8.0.56/248; Desktop 0.7.0.108; ostatnie workflow dla HEAD po zmianach: [Android #1950](https://github.com/edwinkarolczyk/Edhome/actions/runs/37767797526), [Desktop #249](https://github.com/edwinkarolczyk/Edhome/actions/runs/37767797670). CI wcześniejszego Android [#1944](https://github.com/edwinkarolczyk/Edhome/actions/runs/37767321384) przeszło, ale nie obejmuje ostatniego patcha konfliktów ani edycji Desktopu.

**Pozostało P0:** (1) dwa kierunki zmian i idempotencja płatności oraz nadpłat; (2) rozstrzyganie konfliktów per pozycja/operacja bez wyboru całego snapshotu, w tym jednoczesnych zmian offline; (3) końcowe testy na fizycznych urządzeniach oraz odbiór backup→restore. Od tej chwili każdą finansową zmianę po obu stronach należy testować na kopiach danych.

### Zadania do wykonania w etapie 5C

1. Zidentyfikować aktualne ścieżki zapisu i odczytu budżetu Androida i Desktopu; rozdzielić przejściowy full snapshot od docelowego strumienia zmian.
2. Umożliwić edycję wspólnego Budżetu na PC bez utworzenia drugiej niezależnej księgi, bez nadpisywania danych Androida i bez kasowania starego pliku Desktopu.
3. Przesyłać przyrostowo wpisy, wystąpienia miesięczne, odbiorców, nadpłaty oraz historię **w obu kierunkach**; zachować identyfikatory, idempotencję i bezpieczne kasowanie.
4. Rozstrzygać konflikty po zmianach offline na dwóch urządzeniach. Nie stosować bez ostrzeżenia zasady „ostatni zapis zawsze wygrywa”, jeżeli może utracić płatność, korektę albo historię.
5. Chronić kwoty wpłat, nadpłaty, zaległości, ręczne zamknięcia i przeniesienia między miesiącami przed podwójnym naliczeniem.
6. Sprawdzić migrację z poprzednich wersji, backup ZIP → restore i ponowną synchronizację **bez pełnej utraty albo nadpisania danych**.
7. Uruchomić testy kontraktowe, regresję CI, następnie przygotować scenariusz odbioru Android ↔ Desktop na fizycznych urządzeniach (offline, restart, konflikt, ponowne połączenie, restore).

**Nie wolno zamykać 5C wyłącznie po przejściu CI.** Wymagane są wyniki testów użytkownika na urządzeniach i jednoznaczne sprawdzenie spójności danych.

## Aktywny temat 2: Projekty — stabilizacja P0

**Źródło szczegółów:** [ROADMAP.md — Projekty](ROADMAP.md). Priorytet równoległy; stan nieodebrany.

- Wymagania czynności: checkbox spełnione/cofnij, edycja i usuwanie bez obejść, w Androidzie i Desktopie; przeliczanie blokad po każdej zmianie.
- Zależności: zbiorczy edytor zaznaczeń, nazwy blokujących i odblokowywanych czynności, ochrona przed pętlami i zależnościami do siebie.
- Timer Start/Stop: rozliczanie faktycznego czasu, czytelne statusy zielony/żółty/czerwony, trwałe powiadomienie i przejście bezpośrednio do czynności.
- Regresja wejścia w ekran wymagań projektu (wcześniej zgłaszano awarię), trwałość danych, cofnięcie czynności, operacje offline oraz backup/synchronizacja.
- Nie oznaczać Projektów jako gotowych do codziennej pracy bez fizycznego odbioru funkcjonalnego.

## Pozostałe obszary i zaległe kontrole

| Obszar | Uwaga / następny test |
|---|---|
| LAN Android ↔ Desktop | Sprawdzać realnie transfer w obie strony, także po restarcie i bez ręcznego wywoływania; sam status „połączono” nie oznacza poprawnej synchronizacji |
| Magazyn, QR/NFC, remanent | Zachowanie identyfikatorów, historia przenosin, zdjęcia/miniatury, backup i konflikty przy synchronizacji; szczegóły w roadmapie |
| Pojazdy / maszyny | W backlogu: km/motogodziny, interwały serwisu i zadania przypominające; nie nazywać ich gotową funkcją |
| Stable 1.0 | Bez promocji z `beta` na `main` bez zgody i odrębnych testów migracji, podpisu, wydania i odtwarzania |

## Rejestr prac i blokad

| Data | Etap / zmiana | Potwierdzenie |
|---|---|---|
| 2026-10-08 | 5A: struktura SQLite + historia Budżetu; 5B: Desktop — odczyt wspólnego Budżetu | Android Beta `0.8.0.55/247`; Desktop Beta `0.7.0.106`; CI [Android](https://github.com/edwinkarolczyk/Edhome/actions/runs/37764090792) i [Desktop](https://github.com/edwinkarolczyk/Edhome/actions/runs/37764090766) — success |
| 2026-10-08 | 5C i odbiór 25/25 pozostają otwarte | [Roadmapa](ROADMAP.md); brak potwierdzenia testu fizycznego i pełnego dwukierunkowego zapisu |
| 2026-10-08 | Utworzono stały plik statusu projektu | `docs/EDHOME_STATUS.md`, gałąź `beta`; commit widoczny w historii GitHub tego pliku |

| 2026-10-08 | 5C — historia append-only, konflikty i pierwsza edycja wspólnego Budżetu PC | Commity: `3160b783`, `a58d37b1`, `9a9674de`, `4ba83a06`, `2d13d457`, `8caa634d`; nowe testy JUnit/kontrakt; wynik końcowego CI oczekiwany. |

## Zasady aktualizacji tego pliku przy każdym następnym etapie

1. **Przed zmianą kodu:** odczytaj ten plik, [ROADMAP.md](ROADMAP.md), aktualny HEAD `beta`, wersje Android/Desktop oraz wynik najnowszych CI.
2. **Po wdrożeniu:** popraw datę, wersje, stan etapu, numer commita, konkretny test i wynik. Zapisz, co faktycznie zmieniono; nie oznaczaj planów jako wdrożonych.
3. **Przy błędzie lub ograniczeniu:** zapisz dokładny komunikat, numer nieudanego workflow, co zostało przerwane i jakie dane są zagrożone. Powiedz o blokadzie użytkownikowi w bieżącej rozmowie.
4. **Po akceptacji:** odróżnij „kod wdrożony”, „CI success”, „urządzenie przetestowane” i „funkcja odebrana”. Bez testów nie wolno przechodzić automatycznie do „gotowe”.
5. **Na koniec odpowiedzi użytkownikowi:** wskaż aktualny etap, następny krok i odnośnik do tego pliku; przy większych zmianach utwórz osobny commit aktualizujący status.
6. **Nowa rozmowa ChatGPT:** zacznij od polecenia „Otwórz `docs/EDHOME_STATUS.md` w `edwinkarolczyk/Edhome` na `beta` i kontynuuj od następnego kroku”. Nie trzeba odtwarzać całego poprzedniego czatu.
7. **Bez fikcyjnej automatyzacji:** plik aktualizuje się podczas faktycznie wykonanych prac z dostępem do GitHub, **nie w tle bez uruchomionej sesji/zadania**.

**Źródła szczegółowych decyzji:** [ROADMAP.md](ROADMAP.md), historia commitów i uruchomienia GitHub Actions. W razie sprzeczności najnowszy sprawdzony stan kodu i testów ma pierwszeństwo przed nieaktualnym opisem historycznym, natomiast decyzje o zakresie muszą być zgodne z ustaleniami użytkownika.
