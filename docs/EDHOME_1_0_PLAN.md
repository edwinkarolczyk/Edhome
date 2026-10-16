# EDHOME 1.0 — 9 dni do wydania

**Data planu:** 2026-10-16  
**Termin docelowy:** niedziela **2026-10-25** (9 dni kalendarzowych)  
**Cel:** EDHOME **Android 1.0.0** oraz gotowość odpowiedniego wydania Desktop po weryfikacji. Nie deklarować wydania stabilnego, zanim przejdą bramki bezpieczeństwa.  
**Stan wejściowy:** Android Beta **0.8.0.119 / 311**, Desktop Beta **0.7.0.121**, GitHub Actions Android Beta PASS 117/117; obecne Android API22+ i lokalna baza SQLite v48.  
**Kanały:** wszystkie prace na `beta`; **`main` (Stable) bez zmian, dopóki użytkownik nie zaakceptuje promocji stabilnego wydania.** Numer Android `versionCode` musi być >311 (dla 1.0.0 minimum 312), numer nazwy i manifest/wydanie zawsze zgodne.

## Cele obowiązkowe 1.0

1. **Dane i odzyskiwanie P0** — aktualizacja bez utraty danych; eksport ZIP modułowy; pełny i selektywny restore (w tym migracje starszych kopii), brak niezamierzonego nadpisywania bieżących danych, sprawdzalne odtworzenie w odizolowanym środowisku.
2. **Budżet / PayCheck P0** — dodawanie w Budżecie, automatyczne przeniesienie do PayCheck „Do potwierdzenia”, import pełnej historii bankowej z zachowaniem znaków +/- i dat, możliwość usuwania nieprzypisanych importów, dopasowania 1:1, wiele przelewów → jedna pozycja (np. 200+300=500) i jedno → kilka, nadpłaty/niedopłaty bez błędnego przenoszenia, saldo tylko po potwierdzeniu, brak duplikatów importu.
3. **Projekty i Czynności P0** — miniatury, przytrzymanie → Edytuj/Usuń, kaskadowe usuwanie bez kasowania rzeczy z Magazynu, zależności przy wykonaniu/cofaniu, Start/Stop, działanie checkboxa bez kliknięcia całego tekstu, animacja 1 sekundy, ograniczenie czynności do bieżącego drzewa projektu.
4. **Moduły bazowe** — Magazyn, Miejsca, QR/NFC, Kalendarz, Odpady, powiadomienia i szybka czynność: brak crashy P0, brak cichej utraty danych, poprawna obsługa błędów uprawnień i offline.
5. **Desktop i LAN** — czytelny status zgodności Budżetu/PayCheck z Androidem, kontrakty testowe importu i synchronizacji, poprawne patch/tombstone i miniaturki. Prawdziwy dwukierunkowy test Android↔Desktop pozostaje osobną bramką; bez dostępu do fizycznego PC nie deklarować jego wykonania i nie ukrywać ograniczenia w notatkach wydania.

## Harmonogram i bramki dzienne

| Dzień | Data | Obszar | Wynik |
| --- | --- | --- | --- |
| 1 | 16 X | Zamrożenie zakresu i triage P0/P1 | Lista P0 z priorytetami, dokument statusowy, brak nowych modułów |
| 2 | 17 X | Budżet/PayCheck | Reprodukowane i poprawione krytyczne scenariusze potwierdzenia/importu, regresje |
| 3 | 18 X | Backup/restore/migracje | Test pełnego i selektywnego ZIP, stare kopie, bezpieczny rollback |
| 4 | 19 X | Projekty/Czynności | Zależności, usuwanie, miniatury, wykonanie i cofanie, testy |
| 5 | 20 X | NFC/QR, Kalendarz, powiadomienia | Brak P0 i testy zachowania podstawowych funkcji |
| 6 | 21 X | Desktop/LAN i dane finansowe | Parzystość funkcji podstawowych, testy patchy i zgodności, oznaczenie niezweryfikowanego sprzętu |
| 7 | 22 X | Ustawienia, wydajność, dostępność | Uproszczenie UI bez regresji i zamknięcie błędów P1 blokujących wydanie |
| 8 | 23 X | Kandydat RC1 | Kompilacja, lint API22, testy, podpisany artefakt, plan rollback |
| 8b | 24 X | Odbiór RC1 i poprawki końcowe | Test aktualizacji na istniejącej bazie, kopia/restore, brak P0 |
| 9 | 25 X | Decyzja wydawnicza | Jeśli bramki zielone: 1.0.0; w przeciwnym razie RC z jawnie wymienionymi blokadami — bez pozornego „Stable” |

## Bramka GO/NO-GO dla 1.0

- [ ] **0 otwartych P0**: crash przy wejściu/obsłudze kluczowych modułów, utrata danych, duplikacja/nieprawidłowe saldo, wadliwy restore.
- [ ] Testy CI Android Beta i Desktop Beta PASS, kompilacja i zgodność API22.
- [ ] Podpis APK tym samym certyfikatem, który aktualizuje zainstalowaną Betę; aktualizacja **bez odinstalowywania**; poprawny `versionCode`, `beta-manifest.json`, link i SHA256.
- [ ] Praktyczny test Android: Czynności/Projekty, Budżet/PayCheck, powiadomienia, QR/NFC jeśli sprzęt pozwala, backup/restore **tylko na danych testowych lub po wykonaniu kopii**.
- [ ] Testy ZIP: v48 i starsze obsługiwane formaty, pełne i selektywne, odtworzenie po awarii, zgodność wsteczna; ślady z testu.
- [ ] Jeśli Desktop ma być objęty deklaracją 1.0: test uruchomienia, edycji Budżetu/PayCheck, aktualizacji i synchronizacji Android↔Desktop na realnych urządzeniach. **Bez tego Desktop nie może zostać opisany jako zweryfikowany produkcyjnie**.
- [ ] Raport „co działa / co nie”, lista P1/P2 przeniesionych do 1.1, instrukcja instalacji, lokalizacji kopii i odzyskania danych.
- [ ] Akceptacja promocji zmian z `beta` do `main` przez użytkownika.

## Zamrożenie zakresu

**Nie dodawać do 1.0:** nowych modułów, nowej integracji Supla/temperatur, funkcji tylko dekoracyjnych, zaawansowanego rezerwowania przedziałów godzinowych, przebudowy architektury ani zmian synchronizacji bez testów. Wszystko niezwiązane z P0/P1 przenieść do **1.1**.

**Zasada organizacyjna:** każdy zamknięty etap kończyć rzeczywistym commitem, testami i aktualizacją `docs/EDHOME_STATUS.md` z wersjami, CI, ryzykami i następnym krokiem. Harmonogram nie oznacza automatycznego wykonywania prac ani gwarancji 1.0 bez testów. Użytkownik ma ograniczony dostęp do Desktopu; preferować w tym czasie testy automatyczne i Android.
