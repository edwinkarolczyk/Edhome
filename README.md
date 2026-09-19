# EDHOME — Idea by Edwin

> **Status gałęzi `beta`:** dostępny jest **prototyp Android EDHOME 0.1.2-beta.1**, z panelem, testową spiżarnią, prostą listą czynności, lokalną diagnostyką pierwszym kreatorem remanentu oraz klientem aktualizacji DEV. Kalendarz, skaner aparatu, PayCheck, SUPLA i synchronizacja pozostają w planie. Żaden wariant nie został opublikowany w Google Play.

**UWAGA — konflikt podpisu APK (19.09.2026):** kolejne kompilacje Debug otrzymywały różne certyfikaty. **Nie odinstalowuj działającej wersji, jeżeli zależy Ci na danych.** Kod 0.1.3-beta.1 i poprawiony workflow czekają na skonfigurowanie trwałego prywatnego klucza przez właściciela repozytorium; CI świadomie nie publikuje nowych APK bez tego klucza. To **nie naprawia wstecz** podpisu istniejącej instalacji. [Przyczyna, kroki ratunkowe i konfiguracja podpisu](docs/AKTUALIZACJE_PODPIS_I_ODZYSKIWANIE.md).

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

- Motywy **Grafitowy** (domyślny), **Leśny**, **Jasny**.
- Responsywny telefon ok. 6,4 cala oraz tablet; kalendarz bez stałego dolnego paska.
- Ikony wariantów Stable i Beta, zaokrąglone rogi; czytelny napis **BETA** w wydaniu testowym.
- Nazwa gospodarstwa użytkownika niezależna od marki EDHOME.

## Gałęzie i publikacja

- `main` — zatwierdzona dokumentacja i w przyszłości stabilny kod oficjalny.
- `beta` — rozwój, testy i przygotowywanie zmian przed przeniesieniem na `main`.
- Beta DEV może mieć oddzielny pakiet i dane; beta Google Play może używać pakietu oficjalnego w kanale testowym.
- Proponowane identyfikatory (do potwierdzenia przed publikacją): `com.edwinkarolczyk.edhome` i `com.edwinkarolczyk.edhome.beta`.

## Pierwsza beta i pliki do zaglądania

- [EDHOME 0.1.2-beta.1 — aktualizator Beta/Stable i wymagany hosting](docs/BETA_0_1_2.md)
- [EDHOME 0.1.1-beta.1 — wcześniejsza beta i remanent](docs/BETA_0_1_1.md)
- [Zbiorczy rejestr ustaleń](docs/SPECYFIKACJA_CALOSC.md)
- [Roadmapa](docs/ROADMAP.md) i [architektura](docs/ARCHITEKTURA.md)
- [Budowanie APK w GitHub Actions](https://github.com/edwinkarolczyk/Edhime/actions/workflows/android-beta.yml)

**Bezpieczeństwo danych:** używaj wyłącznie danych testowych. Debug APK może mieć inny podpis niż poprzednia instalacja, a odinstalowanie usuwa lokalną spiżarnię/czynności/PIN. Brak eksportu bazy użytkownika.

## Następne kroki

Na gałęzi `beta`: zapisać model obiektów i relacji, plan etapów, kontrakty synchronizacji, politykę prywatności, dopiero potem wybrać stos Android i przygotować uruchamialny szkielet. Przed przenoszeniem funkcji PayCheck i Trenera 2 wykonać audyt ich konkretnych wersji i licencji użytych zależności.

**Uwaga o nazwie repozytorium:** adres GitHub jest na razie `edwinkarolczyk/Edhime` (pisownia inna niż marka EDHOME). Nie zmieniono nazwy repozytorium.
