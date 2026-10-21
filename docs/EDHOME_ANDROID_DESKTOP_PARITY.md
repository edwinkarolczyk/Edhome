# EDHOME — zgodność Android ↔ Desktop (beta)

> Dokument roboczy. Android Beta jest punktem odniesienia dla funkcji wspólnych, ale numery wersji obu aplikacji rozwijają się niezależnie. „Moduł dostępny” **nie oznacza** pełnej zgodności funkcji i danych.

## Punkt odniesienia — 20.10.2026

- Android Beta: **0.8.1.140 / 333** (`app/build.gradle`).
- Desktop Beta: **0.7.0.133** (`desktop/build.gradle`).
- Synchronizacja: LAN Hub, porównywanie pól/rekordów i metadanych, import/export ZIP; każdy nowy typ pola wymaga kontraktu Android↔Desktop.
- Gałąź rozwoju: `beta`. **`main`/Stable bez zmian**, dopóki nie będzie osobnej zgody.

## Wstępna macierz zgodności (na podstawie istniejącego kodu)

| Obszar | Obecny stan Desktop Beta | Co sprawdzić / uzupełnić | Priorytet |
|---|---|---|---|
| Synchronizacja Android ↔ Desktop | Implementacja Hub i testy; poprawki schematu nowych projektów w 0.7.0.133 | Test fizyczny Android→PC i PC→Android; automatyczne ponowienie, bez fałszywego potwierdzenia | **P0** |
| Projekty i podprojekty | Edytor Desktop oraz czynności, praca/czas i część powiązań; 0.7.0.133 uzupełnia wymagane pola projektu | Porównać obsługę zależności, ukrywania czynności, inteligentnego planowania, statusów i miniaturek | **P1** |
| PayCheck / Budżet | Ekran PayCheck oraz kod obsługi budżetu i historii na Desktop | Porównać pełny przepływ Budżet→PayCheck, import PDF/CSV, grupowanie 2→1/1→2, potwierdzenia, saldo i zgodność ZIP | **P0/P1** |
| Zadania / Czynności / Kalendarz | Widoki i edycja na Desktop istnieją | Porównać automatyczne znajdowanie terminów, wykonawców, grafik, przypomnienia i blokady | **P1** |
| Magazyn, QR/NFC, Miejsca | Widoki Magazynu, Miejsc i Skanera istnieją | Sprawdzić komplet operacji skanowania, wypożyczeń/zwrotów, lokalizacji i powrotów na miejsce | **P1** |
| Pojazdy / Odpady / Ogród | Moduły Desktop istnieją | Audyt funkcji szczegółowych, terminów, kosztów i integracji z resztą EDHOME | **P2** |
| Energia i ogrzewanie | Obecnie ekran przygotowawczy (`placeholderPage`) | Dopiero po potwierdzeniu wspólnego modelu danych Android | **Do analizy** |
| SUPLA | Obecnie ekran przygotowawczy (`placeholderPage`) | Nie imitować połączenia z SUPLA; wymaga osobnej integracji | **Do analizy** |
| Aktualizacje / Kopie / Diagnostyka | Każda platforma ma własny updater i diagnostykę | Powiązać testy wersji schematu, kopii danych i pobierania logów Android na PC | **P0** |

### Zasada nowej funkcji w Android Beta

1. Przed wdrożeniem sprawdzić: czy zmienia schemat SQLite, kontrakt Hub/JSON, migracje, ZIP, ustawienia, powiadomienia, UI i logikę.
2. Jeżeli dotyczy danych wspólnych — dodać test kontraktowy i poprawkę Desktop Beta przed deklarowaniem pełnej zgodności.
3. Jeśli Desktop nie obsługuje funkcji, wpisać brak/ograniczenie tutaj; brak funkcji nie może niszczyć rekordów przesyłanych z Androida.
4. Jeden minimalny patch Desktop Beta na przebieg kontroli, uruchomić testy/CI; udokumentować commit i wynik publikacji.
5. Nigdy nie kasować danych ani nie wymuszać pełnego zastąpienia bazy przy zwykłym dodaniu projektu/czynności.
6. Po każdej zmianie wykonać test obustronny: nowy wpis Android→Desktop, edycja Desktop→Android, kopia ZIP i sprawdzenie braku duplikatów/konfliktu.

### Historia kontroli

- **20.10.2026**: utworzono matrycę. Ostatni potwierdzony błąd: Desktop generował wiersze projektów bez pól `thumbnail_base64`, `labor_rate_grosz`, `show_in_main_tasks`; poprawione w Desktop Beta **0.7.0.133**, test JUnit schematu projektu PASS w CI. Test na realnych urządzeniach jeszcze do potwierdzenia. Android Beta **0.8.1.140 / 333** opublikowany.
- Kolejny przebieg: zweryfikować nowe commity Android i przyrost braków w Desktop; wdrażać według P0→P1→P2.

## Kontrola 21.10.2026 — Android 0.8.1.141 / 334 ↔ Desktop 0.7.0.134

- Punkt odniesienia: `beta` HEAD `87bccf715f1e7fcbe425824fa2054fb0bf6dd2bf` ([commit](https://github.com/edwinkarolczyk/Edhome/commit/87bccf715f1e7fcbe425824fa2054fb0bf6dd2bf)); Android `app/build.gradle` = 0.8.1.141/334, Desktop `desktop/build.gradle` = 0.7.0.134. Commit HEAD aktualizuje manifest wydania, nie implementację. Numery platform są niezależne.
- Android `beta-manifest.json` wskazuje [APK 0.8.1.141](https://github.com/edwinkarolczyk/Edhome/releases/download/beta-v0.8.1.141/edhome-beta.apk) i SHA-256 `140c299decf71dbca6dfb6267ed17f6cfbdd83cf1232c68fcabd92fa56891c84`. Nie zweryfikowano pobrania assetu. Desktop workflow deklaruje 0.7.0.134 i publikację `desktop-beta-latest`, lecz nie potwierdzono wersji rzeczywistego EXE.
- **CI aktualnych wersji: niepotwierdzone.** Odczyt statusów HEAD zwrócił pustą listę; endpoint przebiegów Actions obejmuje tylko PR, a nie push. Nie oznacza to ani PASS, ani FAIL. Workflow Android zawiera `check_db_contract.py`, `check_project_visibility_priority_51.py` i `check_hub_diagnostics_contract.py`; Desktop zawiera kontrakty Python, JUnit `:desktop:test`, build i publikację EXE.
- **Urządzenia fizyczne: brak potwierdzonego testu E2E dla 0.8.1.141 ↔ 0.7.0.134.** Starsze logi potwierdzają archiwizację diagnostyki, nie pełną dwukierunkową synchronizację danych.
- **Zmiana od poprzedniej kontroli:** Android inicjuje diagnostykę w `EdhomeApplication.onCreate`; Desktop 0.7.0.134 rejestruje handler wyjątków wątków/EDT bez zapisywania prywatnych komunikatów. Bez nowej migracji, zmian finansów, ZIP ani kontraktu Hub; test statyczny jest w obu workflow, ale wynik bieżącego CI niepotwierdzony.

| Obszar | Potwierdzone fakty i luki na `beta` | Ocena | Priorytet |
|---|---|---|---|
| Hub JSON / synchronizacja | Patch `edhome-record-patch` v2, `syncRecords` (`syncUuid`, `rowKey`, `revision`, `updatedAt`, `deletedAt`, `rowHash`), CAS, SHA, tombstones, konflikt 409; testy kontraktowe i runtime w repo. Brak fizycznego Android→PC→Android, konfliktu i restartu. | Kontrakt w kodzie; E2E **niepotwierdzone** | **P0** |
| Schemat, migracje, ZIP | Android SQLite **v51**, migracja 50→51 `projects.show_in_main_tasks DEFAULT 1`; Desktop szablon projektu ma `thumbnail_base64`, `labor_rate_grosz`, `created_at`, `show_in_main_tasks`. Testy w kodzie; restore starego ZIP v50/v51 i selektywnego na urządzeniach niepotwierdzony. | Pola w kodzie zgodne; E2E **niepotwierdzone** | **P0** |
| Budżet / PayCheck | Desktop `DesktopBudgetOutboundDelta` obsługuje wyłącznie zmiany budżetu, a inne modyfikacje tabel kieruje do osobnej ścieżki; Android wymaga zatwierdzenia bankowych rozliczeń i blokuje snapshot/restore przy niedokończonym `bank_group_settlement_recovery`. Brak fizycznego testu 2→1, 1→2, nadpłat, salda, ZIP i konfliktu. | **Częściowo**, krytyczny odbiór otwarty | **P0** |
| Projekty / Czynności | Podprojekty, sesje robocizny, ukrywanie czynności i zależności mają pola wspólne. **Poprawione w Desktop Beta 0.7.0.135:** nowe zwykłe czynności i czynności projektu otrzymują 20 minut; istniejące zapisy bez zmian. Test kontraktowy uruchamia się w CI. Fizyczne potwierdzenie nadal otwarte. | **Częściowo** | **P1** |
| Inteligentny planer / Kalendarz | Android `SmartTaskPlanner.proposeForBatch`, propozycje rano/wieczorem, 28 dni, zatwierdzanie; brak analogicznych `SmartTaskPlanner`/`proposeForBatch` w `EdhomeDesktop.java`. Kalendarz Desktop jest dostępny, ale nie ma potwierdzonej równoważności planowania i przypomnień. | **Brak pełnej funkcji Desktop** | **P1** |
| Magazyn QR/NFC i Miejsca | `nfc_links`, skaner Desktop, hierarchia miejsc, `frost_risk`, `storage_items.return_required/return_after_days/taken_at`, operacje Wzięte/Odłożone; fizyczny odczyt NFC, powiadomienia i konflikty lokalizacji niepotwierdzone. | **Częściowo** | **P1** |
| Pojazdy / Odpady / Ogród | Moduły obecne; Desktop Ogród zawiera `garden_plantings` i `garden_harvests`; pełna zgodność operacji, terminów, kosztów i przypomnień niezweryfikowana. | **Częściowo / do testu** | **P2** |
| Diagnostyka | Android→Hub→Desktop: archiwum, ACK SHA-256 i dopiero potem usuwanie przesłanych logów z telefonu. Handlery startowe 0.8.1.141/0.7.0.134 w kodzie, brak testu na tych urządzeniach. | Kod częściowo zgodny; E2E **niepotwierdzone** | **P1** |

**Warunek P0 PASS:** fizycznie sprawdzić utworzenie Android→Desktop, edycję Desktop→Android, równoczesny konflikt bez utraty danych, odłączenie/restart/zmianę IP, transakcję bankową i saldo, ZIP stare/nowe oraz brak duplikatów. Nigdy nie wymuszać pełnego zastąpienia bazy lub czyszczenia danych. Przed nowym kodem Desktop najpierw potwierdzić aktualne CI, podpisane paczki i kontrakt v51. **Ten przebieg: tylko dokumentacja; bez zmian kodu, migracji, `main` i bez dopisywania `EDHOME_STATUS.md`.**

## Zmiany 21.10.2026 — diagnostyka P0 i parytet 20 minut

- Android 0.8.1.142 / 335: przy imporcie bankowym jednoznaczny ślad START / COMMITTED / FAILED, przy eksporcie ZIP awarie, a przy restore START / COMMITTED / FAILED. Bez danych prywatnych w metadanych.
- Desktop 0.7.0.135: naprawione domyślne 20 minut nowej czynności i wklejanej czynności projektu.
- Testy/realne urządzenia wymagają odbioru; nie deklarować pełnej zgodności 1.0. Stable `main` bez zmian.
