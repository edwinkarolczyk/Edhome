# EDHOME 0.1.0-beta.1 — pierwsza beta Android

> **Wersja rozwojowa, nie produkcyjna.** Instalacja `com.edwinkarolczyk.edhome.beta`, dane odrębne od oficjalnego identyfikatora `com.edwinkarolczyk.edhome`. Nie zapisuj rzeczywistych ważnych informacji, finansów ani PIN-u używanego gdzie indziej.

## Co znajduje się w tej becie

- Natywna aplikacja Android (telefon i tablet), podstawowy przewijalny panel EDHOME.
- Wybór motywu: Grafitowy, Leśny, Jasny. Własna nazwa gospodarstwa.
- Lokalna konfiguracja PIN (PBKDF2 z losową solą), odblokowanie po powrocie z aplikacji. **Brak odzyskiwania PIN-u oraz audytu bezpieczeństwa**; nie jest to finalny system uprawnień.
- Prosta lista samodzielnych czynności: dodaj, odhacz, usuń długim przytrzymaniem.
- Prototyp spiżarni: dodaj produkt, zwiększ/zmniejsz ilość sztuk; lokalna SQLite. Na razie nie ma jednostek, lokalizacji, historii korekt ani pełnego modelu magazynu.
- Placeholdery „Kalendarz” i „Skaner / Remanent”, **jawnie niedziałające**, aby nie mylić planów z gotową funkcją.
- Brak uprawnienia INTERNET; żadnych usług bankowych, kont użytkownika w chmurze ani SUPLA.

## Diagnostyka — TYLKO BETA

Automatycznie zapisywany prywatny plik `files/diagnostics/edhome-beta.log` i przy przepełnieniu `edhome-beta.previous.log`; log rotowany po ~1 MB, eksport/kopiowanie ostatnich 160 tys. znaków. Diagnostyka rejestruje uruchomienie, widoki, odblokowanie (tylko sukces/błąd), utworzenie/odhaczenie/usunięcie zadania, operacje spiżarni, zmianę motywu, eksport i klasę/ramki błędu.

**Jak pobrać log:** panel główny → „Diagnostyka BETA” albo Ustawienia → „Diagnostyka BETA” → **Kopiuj log do schowka** (wklej w czacie) lub **Eksportuj plik .txt** (wybierz miejsce zapisania w Androidzie). Podgląd w aplikacji obejmuje końcowy fragment logu. Całość jest lokalna; nic nie jest wysyłane automatycznie.

**Prywatność:** nie logować PIN-ów, nazw rzeczy, produktów, kwot, treści wiadomości bankowych, tokenów ani nieoczyszczonych komunikatów błędu. Log zawiera techniczne zdarzenia i nazwy ekranów. Systemowego `logcat` całego telefonu NIE zbieramy. Eksport może zawierać techniczne informacje o urządzeniu i działaniach, więc użytkownik decyduje komu je udostępnić.

**Stable:** osobny wariant Gradle `stable` ma `BuildConfig.DIAGNOSTICS_ENABLED=false`; nie instaluje crash-handlera, nie zapisuje diagnostycznego pliku, nie pokazuje przycisku ani nie daje eksportu. Wariant stable na gałęzi beta to nadal **prototyp, nie oficjalne wydanie**. CI buduje i publikuje wyłącznie `betaDebug`.

## Jak uzyskać APK

GitHub → [Actions / EDHOME Beta APK](https://github.com/edwinkarolczyk/Edhime/actions/workflows/android-beta.yml) → **zielony** build po commicie kodu → **Artifacts** → `EDHOME-0.1.0-beta.1-apk`; pobierz archiwum ZIP, rozpakuj i zainstaluj `app-beta-debug.apk`. Android może wymagać zgody na instalację spoza sklepu.

Jeśli build jest czerwony albo trwa, nie ma potwierdzonego działającego APK. Debug APK jest wyłącznie do prywatnych testów. Nie obiecujemy podpisu produkcyjnego ani aktualizacji istniejącej instalacji innym kluczem.

## Czego nie ma

Pełnego kalendarza, przypomnień i sezonów, skanowania aparatem QR/EAN, kreatora remanentu, korekt i historii magazynu, PayCheck, prywatności wielu osób, SUPLA, synchronizacji LAN, eksportu/kopii danych, głosowego dodawania i automatycznych aktualizatorów. Roadmapa i specyfikacja opisują plan, nie gotowe funkcje.

## Próba ręczna

Uruchom aplikację i utwórz **testowy** PIN, zmień motyw oraz nazwę gospodarstwa; dodaj/odhacz czynność i dodaj/wyjmij produkt. Przejdź do Diagnostyki, skopiuj log, sprawdź eksport `.txt`. Zamknij i otwórz aplikację, potwierdź prośbę o PIN i zachowanie lokalnych danych.

W razie problemu wklej wyeksportowany log, numer wersji i opis ostatniej czynności. Nie przesyłaj PIN-u.
