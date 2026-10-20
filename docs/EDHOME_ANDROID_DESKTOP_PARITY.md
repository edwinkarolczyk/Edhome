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
