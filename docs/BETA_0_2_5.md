# EDHOME 0.2.5-beta.1 — public GitHub Releases OTA

**Repozytorium Edhome jest już PUBLICZNE.** Użytkownik sam zmienił jego widoczność. Dotychczasowe instrukcje tworzenia dodatkowego EDHOME-Updates oraz sekretu EDHOME_PUBLIC_CHANNEL_TOKEN są w tym wariancie nieaktualne.

## Jak działa aktualizacja

- Kompilacja i podpis pozostają w GitHub Actions. Sekrety klucza podpisu nie są publikowane ani wysyłane do telefonu.
- Workflow `android-beta.yml` ma uprawnienie `contents: write` i używa **wbudowanego** `github.token`, bez personal access token.
- Nowy podpisany APK trafia do GitHub Release oznaczonego `beta-v0.2.5-beta.1` i jako zwykły prywatny artefakt CI. Release kieruje tag na gałąź `beta`, nie na `main`.
- Po weryfikacji, że plik APK jest widoczny w Release, workflow aktualizuje `beta-manifest.json` **tylko na beta**. Manifest zawiera wersję, HTTPS do APK i SHA-256.
- Adres kanału wpisany w APK: `https://raw.githubusercontent.com/edwinkarolczyk/Edhome/beta/beta-manifest.json`. Nie trzeba podawać żadnego HTTPS w aplikacji.
- Klient sprawdza manifest, pobiera wyłącznie nowszy `versionCode`, weryfikuje SHA-256, pakiet i zgodność podpisu z zainstalowaną wersją. Instalację zatwierdza Android. Starym APK 0.2.4 (kod 14) można rozpocząć pobieranie z nowego kanału dopiero po przejściowym ręcznym zainstalowaniu APK 0.2.5 (kod 15): stara aplikacja ma na stałe nieistniejący adres EDHOME-Updates.
- Brak zmiany `main`; gałąź `beta` i repozytorium Releases są jedynymi miejscami publikacji OTA.

## Testy przed deklarowaniem sukcesu

Wymagany pełny wynik Actions: kompilacja Beta/Stable, regresja czynności, zgodność migracji/kopii, kontrola certyfikatu, wydanie GitHub Release i aktualizacja publicznego manifestu. Po tym potwierdzić publiczny odczyt manifestu i obecność APK. Instalacja fizyczna z 0.2.5 do następnej wersji pozostaje do ręcznego potwierdzenia na telefonie.

**Uwaga o prywatności:** Repozytorium `Edhome` jest publiczne, więc jego kod i historia commitów są publicznie czytelne. Klucze podpisu i sekrety Actions muszą pozostać prywatne. Nie należy publikować danych użytkowników.
