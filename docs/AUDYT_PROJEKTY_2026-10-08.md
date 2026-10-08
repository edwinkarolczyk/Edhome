# EDHOME — audyt modułu Projekty (08.10.2026)

**Zakres:** gałąź `beta` repozytorium `edwinkarolczyk/Edhome`; Android, Desktop, SQLite, LAN sync, backup, wymagania i zależności, czas pracy, testy CI.  
**Podstawa:** `docs/ROADMAP.md` → „Projekty — kontrakt działania i odbioru (08.10.2026)” (17 ustaleń, 7 scenariuszy).  
**Metoda:** przegląd realnego kodu i testów w repozytorium, weryfikacja workflow GitHub Actions. **Nie wykonywano testów na fizycznych urządzeniach ani prawdziwego testu LAN/restore**. Zielony CI nie stanowi samodzielnego odbioru E2E.  
**Werdykt:** **🔴 P0 niezakończony**. Moduł istnieje, ale nadal można obejść twarde blokady wykonania z innego ekranu. Nie przekazywać do Stable.

## Potwierdzone problemy

### P0-01 — Android: obejście blokad przez ekran Zadania (potwierdzony defekt)
- **Kod:** `MainActivity.java` → `renderProjectTasks` wykonuje kontrole przed `db.completeTask`, ale ogólna lista zadań też bezpośrednio wywołuje `db.completeTask(id)`. Wspólna metoda `DbHelper.completeTask(long id)` nie sprawdza `ProjectStore.openDependencyCount`, `ProjectPlanningStore.openHardCount` ani `ProjectStore.projectWorkBlockReason`.
- **Skutek:** projektowa czynność z niespełnionym twardym wymaganiem lub poprzednikiem może zostać oznaczona jako wykonana poza ekranem Projekty. Tego nie zabezpieczają kontrole UI.
- **Odtworzenie:** projekt A → zadania „Zakup” i „Montaż”; „Montaż” zależny od „Zakup” oraz wymaganie twarde; wejdź do ogólnych Zadań i zaznacz „Montaż”.
- **Naprawa:** jedna wspólna walidacja w ścieżce zapisu zakończenia dla zadań z `project_id`; komunikat o blokadzie również w widoku Zadania, powrót checkboxa na poprzedni stan.

### P0-02 — Android: ciche zamknięcie sesji pracy przez ogólne „Wykonane” (potwierdzony defekt)
- **Kod:** `DbHelper.completeTask(long id)`: przed sprawdzeniem wykonania wywołuje `ProjectStore.stopWork`, jeśli aktywna sesja. W projektowej karcie przy aktywnej sesji zakończenie jest zablokowane, lecz ogólny ekran Zadania tej blokady nie egzekwuje.
- **Skutek:** pomiar może zostać zamknięty bez wyraźnej zgody użytkownika; ponadto przejście poza Projekty nie wywołuje ścieżki `ProjectWorkNotification.cancel` w tym samym miejscu.
- **Naprawa:** wymagaj jawnego Stop (lub zatwierdzonej operacji „Stop + Wykonane”), oddziel zakończenie zadania od zatrzymania czasu, odśwież powiadomienie; regresja dla wszystkich punktów wejścia.

### P0-03 — Desktop: ogólny edytor może obejść status projektu (potwierdzona luka)
- **Kod:** `EdhomeDesktop.java` → `section("Zadania")` udostępnia edycję pola `done`. `editRow` sprawdza aktywny timer, poprzedniki i twarde wymagania, **ale nie** `desktopProjectWorkBlockReason`; ten warunek jest natomiast w `toggleDesktopProjectCompletion` w module Projekty.
- **Skutek:** różne ścieżki zakończenia czynności stosują odmienne reguły dla projektu wstrzymanego/zakończonego.
- **Naprawa:** jedna wspólna walidacja zapisu statusu, używana przez ogólny edytor i przycisk Projekty.

### P0-04 — synchronizacja Start/Stop, usuwania i cofania: brak odbioru E2E (ryzyko, nie potwierdzona awaria)
- **Kod obecny:** `ProjectStore.startWork/stopWork`, `ProjectWorkNotification`, `SyncRecordStore`, `DesktopHubSync`, `hubEnsureSingleActiveProjectSession`; Desktop generuje wysokie losowe identyfikatory (chroni przed prostymi kolizjami).
- **Brak dowodu testowego:** kompletne testy rzeczywistego Android → Desktop → Android: dwa starty offline, konflikt sesji, Stop z powiadomienia, edycja blokady po obu stronach, usunięcie czynności, cofnięcie wykonania, ponowne połączenie i brak „zmartwychwstałych” rekordów.
- **Naprawa/odbiór:** scenariusz dwukierunkowy i regresja stanu po restarcie oraz po przerwanym połączeniu. Do tego czasu nie oznaczać synchronizacji Projektów jako OK.

### P0-05 — backup/restore Projektów: tabela to nie pełny test (brak odbioru)
- **Kod obecny:** `DataBackup.TABLES` zawiera `projects`, `project_task_dependencies`, `project_task_blockers`, `project_task_work_sessions`, koszty i zasoby; `ProjectPlanningStore.assertIntegrity` kontroluje referencje wymagań.
- **Luka dowodowa:** `tests/check_backup_roundtrip_contract.py` buduje testowy SQLite/JSON, lecz nie dowodzi pełnego ZIP → restore → otwarte sesje → synchronizacja dla Projektów na urządzeniach.
- **Odbiór:** backup projektu z podprojektem, zależnością, wymaganiem, aktywną sesją, wykonanym zadaniem i kosztami; odtwórz na nowej bazie, zweryfikuj wszystkie stany i sync.

## P1 — potwierdzone rozbieżności i ergonomia

### P1-01 — Desktop liczy statystyki tylko z bezpośrednich czynności
- `desktopProjectDetail` korzysta z `desktopProjectTasks(projectId)`, które filtruje tylko `task.project_id == projectId`. `ProjectStore.stats` na Androidzie używa `descendantIds(projectId)`.
- Efekt: projekt z wykonanymi czynnościami wyłącznie w podprojektach może na Desktopie pokazać `0%` i `0 / 0`, a na Androidzie prawidłowy postęp. Naprawić agregację i porównywać na tych samych danych.

### P1-02 — Desktop „Gotowa do wykonania” ignoruje status projektu
- `desktopProjectTaskCard` wyświetla zielone „Gotowa do wykonania”, jeżeli nie ma otwartych poprzedników ani wymagań twardych. Nie sprawdza w tym miejscu `desktopProjectWorkBlockReason`.
- Start zostanie zablokowany poprawnie, ale status w wierszu może być fałszywy.

### P1-03 — kolory czasu na Desktopie nie realizują kontraktu
- `updateDesktopProjectClock` zmienia podpis przed/po przekroczeniu, ale nie ustawia czerwieni po przekroczeniu; `desktopProjectTaskCard` nie stosuje żółtego do przekroczonej i zatrzymanej sesji.
- Kontrakt: zielony w planie, żółty po przekroczeniu przy Stop, czerwony przy aktywnym przekroczeniu. Android ma te rozróżnienia w `bindProjectTimeStatus`.

### P1-04 — pełny formularz Androida nadal ma domyślne 30 minut
- `editTask(null,...)` w `MainActivity.java` ma `new String[]{"normal", "30"}`; także `DbHelper.taskPlanning` daje awaryjnie 30. Szybkie dodawanie ma 20.
- Kontrakt: nowe czynności projektu domyślnie 20 minut. Naprawić warunkowo dla czynności projektowych; zwykłych zadań nie zmieniać bez decyzji.

### P1-05 — Desktop nie prowadzi tej samej historii zakończenia czynności
- `toggleDesktopProjectCompletion` zmienia tylko `done` i `markDirty`; nie zapisuje zdarzenia `task_history`. Android `DbHelper.completeTask` zapisuje `task_history`.
- Efekt: historia wykonania zależy od platformy; brak jednolitego śladu audytowego. Odbiór powinien sprawdzić historię po synchronizacji.

### P1-06 — każda krótka sesja Start/Stop nalicza co najmniej 1 minutę
- `ProjectStore.stopWork` oraz `stopDesktopProjectWork` używają `Math.max(1L, Math.round(.../60000.0))`. Np. sesja kilku sekund naliczy minutę.
- Dotyczy **czasu faktycznie przepracowanego**, nie minimum 20 min szacowanej czynności. Trzeba ustalić uczciwe naliczanie i test sumy kilku krótkich sesji.

### P1-07 — Android: przyciski akcji technicznie leżą poza kartą czynności
- `renderProjectTasks` tworzy `card()`, ale `compactActionRow()` dodaje przyciski bezpośrednio do `body`, nie do `box`. Przy długiej liście wizualnie rozdziela informacje i działania.
- Dopracować jeden spójny wiersz/kartę oraz nie obcinać długich etykiet „Wymagania”.

### P1-08 — Desktop: koszty/zasoby i planer nie mają równoważnego UX
- Android `projects()` udostępnia koszty, rzeczy/pudełka oraz propozycje terminów. Desktop `desktopProjectDetail` koncentruje się na drzewie, czynnościach i zbiorczej edycji.
- Zapis i snapshot nie stanowią równoważnego interfejsu do pracy. Po P0 podjąć decyzję o pełnej parzystości tych ekranów.

## Co jest już rzeczywiście zaimplementowane

- Android: projekty/podprojekty, CRUD wymagań z checkboxami i datą, edytor wielu zależności z kontrolą pętli, karta czasu, Start/Stop, powiadomienie z akcją Stop i deep linkiem, czytelna lista blokad, planowanie oraz agregacja czasu podprojektów.
- Desktop: drzewo projektów, czynności, edycja zbiorcza, wiele zależności z kontrolą pętli, CRUD wymagań, Start/Stop, weryfikacja blokad w podstawowym przepływie, pamięć przewinięcia listy per projekt.
- Backup/sync: schematy nowych tabel istnieją, obsługa rekordów i mechanizm konfliktów są napisane; **bez potwierdzenia E2E**.

## Macierz względem 17 ustaleń roadmapy

| Nr | Funkcja | Stan audytu |
|---:|---|---|
| 1 | Hierarchia projektów | 🟡 Model jest; błędna agregacja Desktop |
| 2 | CRUD czynności i cofanie | 🟡 Obecne; niespójna centralna walidacja |
| 3 | Czas 20–600 min i HH:MM | 🟡 Niespójna wartość domyślna/format PC |
| 4 | Start/Stop + sumy sesji | 🔴 Ciche Stop i brak pełnego testu sync |
| 5 | Zielony/żółty/czerwony | 🟡 Android jest; Desktop niepełny |
| 6 | Trwałe powiadomienie i deep link | 🟡 Implementacja jest, brak testu urządzenia |
| 7 | Edytowalne zależności + brak pętli | 🟡 Implementacja jest; brak E2E |
| 8 | Blokady poprzedników we wszystkich wejściach | 🔴 Obejście przez Zadania na Androidzie |
| 9 | Rodzaje i pola wymagania | ✅ Model i pola w kodzie obu platform |
| 10 | Checkbox/Edytuj/Usuń wymagania | ✅ Interfejs w kodzie obu platform |
| 11 | Semantyka twarde/miękkie | 🔴 Centralne zakończenie Android nie egzekwuje |
| 12 | Sortowanie według wykonalności | 🟡 Kod działa w typowym przepływie; status projektu myli Desktop |
| 13 | Scroll i zachowanie edycji | 🟡 Poprawki w kodzie; brak testu urządzeń |
| 14 | Spójność Android/Desktop | 🔴 Różne reguły ukończenia; brak E2E |
| 15 | Migracja/ZIP/LAN/odtwarzanie | 🔴 Bez odbioru danych i sesji po restore |
| 16 | Planer i okna dostępności | 🟡 Kod jest; brak pełnego E2E |
| 17 | Zasoby i budżet projektu | 🟡 Android ma UI; Desktop ograniczony |

`✅` = obecność kompletnego zakresu interfejsu/modelu w kodzie, **nie odbiór na sprzęcie**.  
`🟡` = częściowo zrobione lub wymaga testu.  
`🔴` = potwierdzona luka P0 albo brak krytycznego testu danych przed odbiorem (rozróżnione powyżej).

## CI i ograniczenia testów

- Android: workflow `EDHOME Beta APK`, zielony run **#37747901298** (commit `8f0e0b7242`, wersja Beta 0.8.0.50 / 242 w manifeście). Dalsze commity zmieniały manifest/roadmapę, nie kod Projektów.
- Desktop: workflow `EDHOME Desktop Beta`, zielony run **#37739456421** (0.7.0.105 w kodzie/Gradle).
- `tests/check_projects_contract.py` i `tests/check_project_planning_contract.py` w większości sprawdzają ciągi znaków/obecność metod. Test `sqlite3` buduje pomocniczą tabelę, nie wykonuje rzeczywistych metod Java z Androida i Desktopu. **Zielony CI nie wykrywa P0-01**.
- Potrzebne testy logiki wykonujące pełny przepływ, najlepiej Android instrumentation/SQLite + testy modelu Desktop + LAN API.

## Kolejność prac — bez dokładania nowych ekranów

1. **Najpierw P0-01 i P0-02:** wymusić walidację zależności/wymagań w centralnej metodzie `completeTask`; ciche Stop usunąć; obsłużyć wyjątek w ogólnych Zadaniach i regresję z dwóch ekranów.
2. **P0-03:** dodać `desktopProjectWorkBlockReason` do wspólnego zapisu `editRow`; testy dla paused/done.
3. **P0-04/P0-05:** dwukierunkowy LAN oraz backup→restore przy aktywnej i zamkniętej sesji; weryfikacja braku duplikatów i odtwarzanych usuniętych rekordów.
4. **P1-01/P1-02/P1-03:** prawidłowe statystyki z podprojektów i statusy/kolory Desktop.
5. **P1-04/P1-05/P1-06/P1-07:** domyślne 20 min, historia, dokładność naliczania, zwarte karty; dopiero później dalsze rozszerzenia.

**Reguła zamknięcia:** P0 naprawione kodem, regresja logiki w CI, zielone buildy oraz siedem scenariuszy odbiorowych zapisanych w roadmapie. Nie modyfikować `main`/Stable bez wyraźnej akceptacji.
