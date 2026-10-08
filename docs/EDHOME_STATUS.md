# EDHOME — aktualny stan prac

> **Stały dziennik kontynuacji projektu.** Przed rozpoczęciem pracy nad EDHOME przeczytaj najpierw ten plik, a po zakończeniu każdego etapu zaktualizuj go w tym samym repozytorium. Szczegółowe wymagania pozostają w [ROADMAP.md](ROADMAP.md); ten plik jest krótkim, aktualnym punktem wznowienia pracy.

## Metryka

| Pole | Stan |
|---|---|
| Ostatnia aktualizacja | 2026-10-08 — dopisano szczegółowy plan stabilizacji Projektów P0/P1; PayCheck/Budżet 5C bez zmian zakresu |
| Repozytorium | `edwinkarolczyk/Edhome` |
| Gałąź robocza | `beta` |
| Stable | `main` — **zakaz zmian, merge i publikowania nowego Stable bez osobnej, wyraźnej akceptacji Edwina** |
| Android Beta | **0.8.0.56 / versionCode 248** — kod, publikacja APK/manifest potwierdzona; [CI Android #1950: success](https://github.com/edwinkarolczyk/Edhome/actions/runs/37767797526) |
| Desktop Beta | **0.7.0.108** — kod, Gradle i workflow zgodne; [CI Desktop #249: success](https://github.com/edwinkarolczyk/Edhome/actions/runs/37767797670), opublikowany instalator |
| Ostatni odczytany HEAD `beta` przed utworzeniem tego pliku | `85fec69ff4a6c154d90bd9e9e0a625e20a9bb2d0` — commit wyłącznie roadmapy |
| Ostatni zweryfikowany CI Android | [run #37764090792](https://github.com/edwinkarolczyk/Edhome/actions/runs/37764090792) — **success**, commit `58bb0d4c9228faa78011ab6afcda509e07750198` |
| Ostatni zweryfikowany CI Desktop | [run #37764090766](https://github.com/edwinkarolczyk/Edhome/actions/runs/37764090766) — **success**, commit `58bb0d4c9228faa78011ab6afcda509e07750198` |
| Bieżący etap | **Projekty — przygotowanie stabilizacji P0 (P0-01 do P0-06); naprawy jeszcze nierozpoczęte w tej serii**. PayCheck/Budżet 5C nadal OTWARTY. |
| Następny krok | Po poleceniu start: **Projekty P0-01 → P0-02 → P0-03 → P0-04 → P0-05 → odbiór P0-06**, każdorazowo commit + test + CI + aktualizacja statusu. Budżet 5C zachowany jako osobny otwarty temat. |

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
- **Wersje:** Android 0.8.0.56/248 — [podpisany APK opublikowany](https://github.com/edwinkarolczyk/Edhome/releases/tag/beta-v0.8.0.56), CI Android #1950 sukces; Desktop 0.7.0.108 — [instalator Windows opublikowany](https://github.com/edwinkarolczyk/Edhome/releases/tag/desktop-beta-latest), workflow Desktop #249 zakończony sukcesem; ostatnie workflow dla HEAD po zmianach: [Android #1950](https://github.com/edwinkarolczyk/Edhome/actions/runs/37767797526), [Desktop #249](https://github.com/edwinkarolczyk/Edhome/actions/runs/37767797670) — oba success. CI wcześniejszego Android [#1944](https://github.com/edwinkarolczyk/Edhome/actions/runs/37767321384) przeszło, ale nie obejmuje ostatniego patcha konfliktów ani edycji Desktopu.

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

## Aktywny temat 2: Projekty — stabilizacja P0 (PRIORYTET NASTĘPNYCH PRAC)

**Stan ogólny:** 🔴 **NIEODEBRANE — naprawy P0 dopiero do wykonania.** Wstrzymać rozbudowę funkcji i P1 do zamknięcia P0. Ustalenie bieżącej rozmowy: najpierw wpisać plan do stałego statusu, **nie rozpoczynać napraw kodu, dopóki użytkownik nie wyda polecenia rozpoczęcia**. 

**Źródła:** [szczegółowy audyt Projektów z 08.10.2026](AUDYT_PROJEKTY_2026-10-08.md), [kontrakt 17 wymagań i 7 scenariuszy odbioru](ROADMAP.md#projekty--kontrakt-działania-i-odbioru-08102026). Przed naprawą zweryfikować aktualny HEAD `beta`, gdyż audyt opisuje wcześniejszy stan kodu. Poniższe statusy to wynik audytu/zgłoszeń, a nie potwierdzenie, że defekty nadal istnieją po późniejszych zmianach innych modułów.

### P0 — lista do wdrożenia w tej kolejności

| ID | Stan | Co należy wykonać | Test / kryterium zamknięcia |
|---|---|---|---|
| **P0-01** | 🔴 Do naprawy | **Android: centralna walidacja wykonania projektowej czynności** — ogólne `Zadania` nie mogą omijać niewykonanych poprzedników, twardych wymagań ani statusu `paused/done` projektu i przodków. Przenieść regułę do wspólnej ścieżki zapisu `DbHelper.completeTask`, zachować stan checkboxa i czytelny komunikat. | Próba ukończenia z ekranu Projekty i Zadania przy niespełnionym poprzedniku, wymaganiu twardym lub wstrzymanym projekcie: odmowa, brak zmiany danych; po usunięciu blokady: sukces. |
| **P0-02** | 🔴 Do naprawy | **Android: bezpieczne Start/Stop** — `completeTask` nie może po cichu wykonać `ProjectStore.stopWork`. Przy aktywnej sesji wymagać osobnego Stop albo jawnego pytania „Stop i zakończ”; zamykać powiadomienie i zachowywać pełną historię czasu; ponowny Start nie resetuje przepracowanego czasu. | Start → próba ukończenia w obu ekranach, stop z aplikacji i powiadomienia, restart, wznowienie, dwie sesje; dokładna suma, brak ukrytego Stop i osieroconego powiadomienia. |
| **P0-03** | 🔴 Do naprawy | **Desktop: wspólne reguły statusu** — ogólny edytor `editRow` oraz przyciski w Projektach mają identycznie blokować wykonanie przy aktywnym timerze, poprzednikach, wymaganiach i statusie wstrzymanego/zakończonego projektu, także nadrzędnego (`desktopProjectWorkBlockReason`). | Zakończenie z każdego miejsca na PC: niedozwolone działania nie zmieniają danych; poprawne wykonanie/Cofnij działa. |
| **P0-04** | 🟠 Do testów E2E / możliwe poprawki | **LAN Android ↔ Desktop** — pełna dwukierunkowa synchronizacja wymagań, zależności, checkboxów, Cofnij, usunięć oraz sesji Start/Stop. Jedna aktywna sesja dla zadania, deterministyczne konflikty offline, brak duplikatów i „powrotów” usuniętych rekordów. Samo „połączono” nie wystarcza. | Test zmian Android→PC i PC→Android, dwa Start offline, konflikt, przerwanie LAN, restart, ponowne połączenie i kontrola identyfikatorów, historii i czasu. Ryzyko jest potwierdzone brakiem odbioru; nie ogłaszać awarii konkretnej ścieżki bez jej odtworzenia. |
| **P0-05** | 🟠 Do testów E2E / możliwe poprawki | **Backup ZIP / restore / migracja** — zachować projekty, podprojekty, wymagania, zależności, rzeczy/pudełka, koszty, czynności, ich wykonanie i wszystkie sesje. Zapewnić atomowość odtwarzania, brak osieroconych wpisów; następnie zweryfikować LAN. | Kopia przed zmianą → odtworzenie w testowej bazie → kontrola powiązań, czasu, historii i sesji → synchronizacja obustronna bez utraty i duplikatów. |
| **P0-06** | 🟡 Poprawka w kodzie; test na urządzeniu nieodebrany | **Crash okna „Projekty → Wymagania”** — zgłoszony w Androidzie 0.8.0.51 (`IllegalStateException`, `MainActivity.showProjectBlockers`, dwa razy `ViewGroup.addView`). Przyczyna: `compactActionRow()` już podpina pasek do `body`, a dialog podpinał go drugi raz. Poprawka zastępuje go lokalnym `new LinearLayout`. | Patch `d1af025`, kontrakt `466247e`, po raz pierwszy przygotowany w 0.8.0.52/244. Trzeba zweryfikować **na aktualnym APK** otwarcie okna z 0, 1 i wieloma wymaganiami, zaznacz/odznacz, Edytuj, Usuń, Zamknij, ponowne wejście; brak `ERROR_UNCAUGHT_main`. Nie utożsamiać zielonego CI z testem fizycznym. |

**P0-01 i P0-02 są krytyczne dla integralności reguł.** Audyt wykazał, że ekran zwykłych Zadań wywoływał `db.completeTask` bez sprawdzenia blokad projektu, a ta metoda mogła automatycznie zatrzymać aktywną sesję. **P0-03** to analogiczna luka w ogólnym edytorze Desktopu. Dla **P0-04/P0-05** najpierw przeprowadzić testy i naprawiać wyłącznie znalezione błędy. **P0-06** jest już poprawione w kodzie, lecz nie ma potwierdzonego odbioru na telefonie.

### Co musi działać po zamknięciu P0

- Hierarchia **projekt → podprojekt → czynności**; brak przechodzenia do `done` z otwartymi zadaniami w podprojektach; poprawne zachowanie `paused`; nazwy projektu i ścieżki czynności.
- Dodawanie, edycja, usuwanie, Wykonane i **Cofnij** (także po przypadkowym zaznaczeniu), z walidacją wszystkich dróg zapisu.
- Zależności: wielokrotny wybór z zaznaczonym aktualnym stanem i jednym **Zapisz**; usuwanie istniejących; blokady, nazwy „Czeka na” i „Odblokuje”, brak pętli, zależności do siebie i między oddzielnymi projektami głównymi.
- Wymagania: kategorie **zakup/materiał, dostawa/oczekiwanie, przygotowanie, zasób, akceptacja i własne**; checkbox zaznacz/odznacz, jawne **Edytuj** i **Usuń** z potwierdzeniem; pola etykieta, rodzaj, opcjonalna data, twarde/miękkie; zmiana daty sama nie oznacza spełnienia; od razu aktualizować blokady i sortowanie.
- Start/Stop i licznik w trwale widocznym powiadomieniu; Stop z powiadomienia; kliknięcie przenosi **do konkretnego projektu i czynności**; historia wielu sesji, pozostały czas, kolory zielony/w-planie, żółty/przekroczenie i Stop, czerwony/przekroczenie i aktywna sesja.
- Kolejność pracy: najpierw **trwające**, potem gotowe odblokowujące inne, potem pozostałe gotowe, zablokowane i wykonane. Czynność nie może pozornie wyglądać na gotową, gdy ma aktywną twardą blokadę.
- Trwałe dane offline, restarty, poprawny backup i wznowienie synchronizacji LAN z PC, bez utraty zmian i bez podwójnego czasu.

### P1 — zarejestrowane, lecz dopiero po P0

| ID | Kolejna poprawka |
|---|---|
| **P1-01** | Desktop: podsumowanie/postęp projektu ma agregować również czynności podprojektów, jak Android. |
| **P1-02** | Desktop: nie wyświetlać „Gotowa do wykonania” przy wstrzymanym/zakończonym projekcie. |
| **P1-03** | Desktop: kolory czasu dokładnie jak kontrakt — zielony, żółty i czerwony; na liście i w podsumowaniu. |
| **P1-04** | Android: domyślne **20 min** dla nowej czynności projektowej również w pełnym formularzu; maksimum 600 min, czytelny `HH:MM` i `0.20 = 20 min`; zachować odrębne reguły zwykłych Zadań. |
| **P1-05** | Desktop: historia wykonania i cofania zgodna z Androidem, bez utraty audytu po synchronizacji. |
| **P1-06** | Sprawdzić dokładność czasu krótkich sesji: obecne `Math.max(1 min)` za kilkusekundową pracę; minimum 20 min dotyczy **planu**, nie faktycznie naliczonego czasu. |
| **P1-07** | Android: zwarta karta czynności z przyciskami wewnątrz karty; bez obciętych etykiet; zachowanie scrolla i wybranej czynności. |
| **P1-08** | Desktop: funkcjonalna zgodność kosztów, rzeczy/pudełek, planera i dostępności z Androidem, bez duplikowania PayCheck. |

**Po P0 także testować planer**: okna użytkownika I zmiana 16:00–21:00, II 08:00–12:00, dzień wolny 09:00–18:00 z wyjątkami; poprzedniki, twarde wymagania, wyłącznie pozostały czas i propozycje terminów do zatwierdzenia (bez automatycznego wykonywania).

### Scenariusze odbioru — wszystkie wymagane przed statusem „gotowe”

1. Projekt A → podprojekt A1 → trzy zadania (zakup → przygotowanie → montaż); dodaj/usuń/przywróć zależność, przetestuj ochronę przed pętlą.
2. Dodaj wymaganie twarde i miękkie; zaznacz, odznacz, edytuj rodzaj/nazwę/datę i usuń; sprawdź zmianę blokad bez awarii okna.
3. Start 20-minutowej czynności, działający licznik i deep link, Stop w powiadomieniu; wznowienie, suma sesji, pozostały czas i kolory po przekroczeniu.
4. Próba wykonania z twardą blokadą i próba ukończenia projektu z niewykonaną czynnością potomną; odmowa we wszystkich ekranach; Cofnij.
5. Usunięcie czynności z zależnościami i sesjami po czytelnym potwierdzeniu; brak osieroconych rekordów i powrotu danych.
6. Backup → restart → restore → Android→Desktop→Android, także po edycji z PC i po konflikcie offline; identyfikatory, statusy i historia identyczne.
7. Brak skakania listy po checkboxie, edycji, zmianie zależności i Stop; praca **offline bez PC** oraz po restarcie telefonu.

**Warunek ukończenia punktu P0:** (a) minimalna poprawka kodu na `beta`; (b) test regresyjny **wykonujący logikę**, nie jedynie szukający fragmentu tekstu; (c) zielony Android/Desktop CI dla właściwego commita; (d) fizyczny test na urządzeniach z wynikiem; (e) aktualizacja tej tabeli z commitami i testem. Po spełnieniu tych wymogów oznaczyć `✅ Odebrane`, nigdy wcześniej.

**Następny krok po poleceniu rozpoczęcia prac:** otworzyć kod `DbHelper.completeTask`, `renderProjectTasks` oraz zwykłe Zadania na aktualnym HEAD `beta` i wdrożyć **P0-01**, następnie **P0-02**, z osobnymi testami/commitami. Budżet miesiąca 5C pozostaje **otwarty, zachowany i nieodebrany** — prace nad nim nie są anulowane. `main`/Stable nienaruszalne bez odrębnej zgody.

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
| 2026-10-08 | 5C — historia append-only, konflikty i pierwsza edycja wspólnego Budżetu PC | Commity: `3160b783`, `a58d37b1`, `9a9674de`, `4ba83a06`, `2d13d457`, `8caa634d`; nowe testy JUnit/kontrakt; Desktop CI #249 i Android CI #1950 — success; oba instalatory dostępne, test fizyczny i odbiór 5C nadal wymagane. |

| 2026-10-08 | Projekty — zapisano plan P0-01–P0-06, P1-01–P1-08 oraz 7 scenariuszy odbioru, **bez modyfikacji kodu** | [Audyt](AUDYT_PROJEKTY_2026-10-08.md); start od P0-01 po poleceniu użytkownika; PayCheck 5C zachowany. |

## Zasady aktualizacji tego pliku przy każdym następnym etapie

1. **Przed zmianą kodu:** odczytaj ten plik, [ROADMAP.md](ROADMAP.md), aktualny HEAD `beta`, wersje Android/Desktop oraz wynik najnowszych CI.
2. **Po wdrożeniu:** popraw datę, wersje, stan etapu, numer commita, konkretny test i wynik. Zapisz, co faktycznie zmieniono; nie oznaczaj planów jako wdrożonych.
3. **Przy błędzie lub ograniczeniu:** zapisz dokładny komunikat, numer nieudanego workflow, co zostało przerwane i jakie dane są zagrożone. Powiedz o blokadzie użytkownikowi w bieżącej rozmowie.
4. **Po akceptacji:** odróżnij „kod wdrożony”, „CI success”, „urządzenie przetestowane” i „funkcja odebrana”. Bez testów nie wolno przechodzić automatycznie do „gotowe”.
5. **Na koniec odpowiedzi użytkownikowi:** wskaż aktualny etap, następny krok i odnośnik do tego pliku; przy większych zmianach utwórz osobny commit aktualizujący status.
6. **Nowa rozmowa ChatGPT:** zacznij od polecenia „Otwórz `docs/EDHOME_STATUS.md` w `edwinkarolczyk/Edhome` na `beta` i kontynuuj od następnego kroku”. Nie trzeba odtwarzać całego poprzedniego czatu.
7. **Bez fikcyjnej automatyzacji:** plik aktualizuje się podczas faktycznie wykonanych prac z dostępem do GitHub, **nie w tle bez uruchomionej sesji/zadania**.

**Źródła szczegółowych decyzji:** [ROADMAP.md](ROADMAP.md), historia commitów i uruchomienia GitHub Actions. W razie sprzeczności najnowszy sprawdzony stan kodu i testów ma pierwszeństwo przed nieaktualnym opisem historycznym, natomiast decyzje o zakresie muszą być zgodne z ustaleniami użytkownika.
