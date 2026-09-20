# EDHOME — Idea by Edwin

> **Status gałęzi `beta`:** EDHOME **0.2.7-beta.1** (`versionCode 17`, SQLite 6). Aktualizacje Beta korzystają z podpisanych GitHub Releases w publicznym repozytorium `Edhome`, a manifest trafia wyłącznie na gałąź `beta`. `main` pozostaje bez zmian. Nie jest wymagane osobne `EDHOME-Updates`, token PAT ani ręczny adres HTTPS w aplikacji. W tej wersji Beta uruchamia się bez PIN-u, a czynności mają pojedynczego opcjonalnego wykonawcę z listy domowników. Stable pozostaje z PIN-em. Repozytorium i kod są obecnie publiczne; prywatne pozostają klucze podpisu, sekrety Actions i dane użytkownika.

**Podpis APK:** EDHOME Beta korzysta teraz ze stałego certyfikatu zweryfikowanego przez GitHub Actions przy wydaniu 0.1.4-beta.1. Nie aktualizuje to wstecz debugowych instalacji z innym podpisem. **Przed usunięciem starej instalacji sprawdź kopię danych.** [Konfiguracja podpisu i zasady odzyskiwania](docs/AKTUALIZACJE_PODPIS_I_ODZYSKIWANIE.md).

**EDHOME** to lokalne centrum zarządzania gospodarstwem domowym: wspólny kalendarz i planer, czynności, finanse osobiste i wspólne, magazyn z QR, ogród, pojazdy, dom i remonty, energia oraz integracja SUPLA. Jedno gospodarstwo może mieć wielu domowników i urządzeń.

## Zasady produktu

- **Offline-first:** podstawowe funkcje, dane i lokalne przypomnienia działają bez internetu; synchronizacja domowników w domowej sieci Wi-Fi. Opcjonalny backup na serwerze domowym dopiero w kolejnym etapie.
- **Prywatność:** osobne finanse PayCheck dla każdego domownika oraz wspólny budżet. Prywatne wydarzenie w kalendarzu drugiej osoby może pokazywać tylko „Zajęty”. Nie synchronizować prywatnych transakcji na wspólny tablet bez uprawnienia.
- **Wspólny rdzeń:** trwałe identyfikatory obiektów, typowane relacje, zdarzenia, reguły, czas/dostępność, uprawnienia, historia oraz bezpieczne migracje. Jedno źródło prawdy zamiast kopii tych samych danych w modułach.
- **Czynności są samodzielne:** mogą istnieć bez przypisania do rzeczy („Zebrać winogrona”), albo być powiązane z wieloma obiektami. Jednorazowe, cykliczne i sezonowe; z historią konkretnych wykonań.
- **Planer podpowiada, nie narzuca:** trzy terminy uwzględniające grafik zmianowy i wyjątki, czas wykonania, wydarzenia, godziny ciszy i dostępność możliwych wykonawców.
- **Google Play od początku w założeniach:** dwa kanały, stabilny i beta, osobne dane testowe, automatyczne testy przed wydaniem. Nie obiecywać cichej instalacji APK wbrew ograniczeniom Androida.

## Moduły docelowe

| Moduł | Zakres |
|---|---|
| Kalendarz i czynności | Pełnoekranowy miesiąc/tydzień/dzień/agenda; terminy z modułów; priorytety; powiadomienia; kosze; grafiki i obowiązki. |
| Finanse / PayCheck | Osobiste i wspólne budżety, cele, raty, OC; powiadomienia bankowe i import wyciągów, bez podwójnego księgowania. |
| Magazyn / QR | Rzeczy, pudełka i nazwane miejsca; osobny QR dla obiektu, lokalizacji i pudełka; ilość opcjonalna; pożyczenia i historia. |
| Dom / remonty | Instalacje, konserwacja, projekty, dokumenty, gwarancje i informacje awaryjne. |
| Ogród | Offline baza upraw po audycie licencji, siew/sadzenie/zbiór, grządki, historia i sezony. |
| Pojazdy | Serwisy, oleje, komplety opon, OC i powiązane cele finansowe. |
| Energia / SUPLA | Początkowo odczyt pomiarów i stanów, preferencyjnie lokalny, jeśli dane urządzenie go obsługuje. |
| Tablet | Przenośny panel i pełna aplikacja, PIN, wygaszanie, konfigurowalne kafelki: zakupy, minutniki, spiżarnia, obowiązki, SUPLA, głos. |

## Wygląd i warianty

- Motywy **Grafitowy** (domyślny), **Leśny**, **Jasny** i **Trener 2** (czarno-czerwony, inspirowany rzeczywistym arkuszem stylów aplikacji).
- Responsywny telefon ok. 6,4 cala oraz tablet; kalendarz bez stałego dolnego paska.
- Ikony wariantów Stable i Beta, zaokrąglone rogi; czytelny napis **BETA** w wydaniu testowym.
- Nazwa gospodarstwa użytkownika niezależna od marki EDHOME.

## Gałęzie i publikacja

- `main` — zatwierdzona dokumentacja i w przyszłości stabilny kod oficjalny.
- `beta` — rozwój, testy i przygotowywanie zmian przed przeniesieniem na `main`.
- Beta DEV może mieć oddzielny pakiet i dane; beta Google Play może używać pakietu oficjalnego w kanale testowym.
- Proponowane identyfikatory (do potwierdzenia przed publikacją): `com.edwinkarolczyk.edhome` i `com.edwinkarolczyk.edhome.beta`.

## Pierwsza beta i pliki do zaglądania

- [Wersja 0.2.7 — Beta bez PIN-u i wykonawcy czynności](docs/BETA_0_2_7.md)
- [Wersja 0.2.6 — priorytet i czas czynności, migracje i test OTA](docs/BETA_0_2_6.md)
- [Wersja 0.2.5 — OTA przez GitHub Releases i jednorazowa aktualizacja przejściowa](docs/BETA_0_2_5.md)
- [Wersja 0.2.4 — poprawiony komunikat niedostępnego kanału aktualizacji](docs/BETA_0_2_4.md)
- [Wersja 0.2.3 — zakres zmian, ograniczenia i test na telefonie](docs/BETA_0_2_3.md)
- [Archiwalne kroki dla osobnego repo dystrybucji — obecnie niepotrzebne](docs/KANAL_AKTUALIZACJI_BETA.md)
- [EDHOME 0.2.1-beta.1 — pionowa siatka 3 × 3](docs/BETA_0_2_1.md)
- [EDHOME 0.2.0-beta.1 — Miejsca, aktualizator, konfiguracja kanału](docs/BETA_0_2_0.md)
- [EDHOME 0.1.8-beta.1 — panel, zadania, spiżarnia, ochrona danych](docs/BETA_0_1_8.md)
- [EDHOME 0.1.7-beta.1 — kafelkowy ekran aktualizacji](docs/BETA_0_1_7.md)
- [EDHOME 0.1.6-beta.1 — wymagana aktualizacja w Beta, Później tylko w Stable](docs/BETA_0_1_6.md)
- [EDHOME 0.1.5-beta.1 — czynności, kalendarz, migracja i testy](docs/BETA_0_1_5.md)
- [EDHOME 0.1.4-beta.1 — kod kopii danych i instrukcja ręcznego przenoszenia](docs/BETA_0_1_4.md)
- [EDHOME 0.1.2-beta.1 — aktualizator Beta/Stable i wymagany hosting](docs/BETA_0_1_2.md)
- [EDHOME 0.1.1-beta.1 — wcześniejsza beta i remanent](docs/BETA_0_1_1.md)
- [Zbiorczy rejestr ustaleń](docs/SPECYFIKACJA_CALOSC.md)
- [Roadmapa](docs/ROADMAP.md) i [architektura](docs/ARCHITEKTURA.md)
- [Budowanie APK w GitHub Actions](https://github.com/edwinkarolczyk/Edhome/actions/workflows/android-beta.yml)

**Bezpieczeństwo danych:** w fazie beta używaj danych testowych i eksportuj kopię przed aktualizacją. Obecna linia podpisanych APK może aktualizować się w miejscu; stare instalacje Debug z obcym podpisem wymagają osobnego odzyskiwania danych. Kopia JSON 0.1.4 (SQLite v2) może zostać zaimportowana przez kod 0.1.8 (SQLite v3).

## Następne kroki

Na gałęzi `beta`: zweryfikować aktualizację 0.2.6 → 0.2.7 i brak PIN-u w Beta na fizycznym telefonie z zachowaniem danych. Następnie 0.2.8: grafik pracy i ręczne wyjątki poszczególnych wykonawców. Dopiero na tej podstawie proponować trzy dogodne terminy. Utrzymać kopie danych oraz testy migracji. Utrzymać kopie danych oraz testy migracji. Przed przenoszeniem funkcji PayCheck i Trenera 2 wykonać audyt ich konkretnych wersji i licencji użytych zależności.

**Repozytorium:** `edwinkarolczyk/Edhome`; nazwa aplikacji **EDHOME — Idea by Edwin**.
