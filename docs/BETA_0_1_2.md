# EDHOME 0.1.2-beta.1 — aktualizator (etap 1)

> **Odróżniać kod aktualizatora od działającego kanału publikacji.** Beta zawiera obsługę aktualizacji, ale prywatny GitHub Actions **nie jest** publicznym serwerem aktualizacji APK. Dopóki nie wskażemy dostępnego bez logowania manifestu HTTPS z plikiem APK podpisanym tym samym certyfikatem, automatyczny kanał nie może sam pobrać kolejnego wydania.

## Zasady, które ustaliliśmy

| Wariant | Zachowanie |
|---|---|
| Stable | Aktualizacje Google Play. Nie instalować w aplikacji APK pobranych z dowolnego adresu. Po publikacji potrzebna integracja z Play In-App Updates / sprawdzanie przy uruchomieniu; w prototypie jest przycisk „Sprawdź w Google Play”. |
| Beta DEV | Po odblokowaniu uruchamia sprawdzanie niedużego manifestu **co 30 sekund tylko gdy aplikacja jest aktywna**, jeśli użytkownik skonfigurował adres HTTPS. Przy wyższej wersji pobiera nowy APK w Android DownloadManager, sprawdza SHA-256 oraz tożsamość pakietu i certyfikat podpisu, pokazuje changelog i oferuje „Instaluj” / „Później”. Instalacja wymaga zgody systemowej. |
| Offline | Codzienna praca EDHOME bez internetu. Beta pozwala ręcznie wskazać APK z pobranych plików Androida; przed pokazaniem instalatora kontroluje numer, identyfikator i podpis. |
| Podpis | Nie wkładać sekretnego klucza produkcyjnego do repozytorium. Cache testowego debug.keystore nie stanowi trwałej gwarancji kompatybilności. Przy konflikcie podpisu nie odinstalowywać jedynej kopii danych. |

## Ustawienie źródła Beta DEV

EDHOME Beta → **Aktualizacje** → wklej pełny adres **HTTPS manifestu JSON**, który można otworzyć bez GitHub tokenu i bez sesji użytkownika. **Nie wpisuj GitHub PAT, hasła ani cookie.** Jeśli pole jest puste, automatyczne sprawdzanie sieci jest celowo wyłączone.

Przykładowa zawartość manifestu (fikcyjne adresy, nie są działającym źródłem):

```json
{
  "channel": "beta",
  "versionCode": 4,
  "versionName": "0.1.3-beta.1",
  "changelog": "Naprawa listy i korekty remanentu.",
  "apkUrl": "https://example.invalid/releases/edhome-beta-4.apk",
  "sha256": "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef"
}
```

Manifest musi być mały (do 16 KiB), zwracać HTTP 200, a oba URL-e muszą używać HTTPS. SHA-256 podać dla **rzeczywiście opublikowanego APK**, nie przykładową wartość powyżej. Żaden automatyczny feed nie został jeszcze uruchomiony. Hosting i stały podpis należy przygotować oddzielnie; prywatnego repo nie zmieniać na publiczne tylko po to, by aktywować aktualizacje.

## Co działa w samej becie

- Wyświetlanie wersji i versionCode, ręczne sprawdzenie manifestu.
- Automatyczne okresowe sprawdzanie **po skonfigurowaniu źródła** w aktywnej Beta DEV.
- Automatyczne pobieranie nowej kompilacji, weryfikacja SHA-256, zgodności pakietu `com.edwinkarolczyk.edhome.beta`, wersji większej od zainstalowanej oraz podpisu. Nie otwierać instalatora, gdy kontrola nie przejdzie.
- Android decyduje o zgodzie na instalację i ewentualnym pozwoleniu na instalowanie z tego źródła. Brak cichej instalacji.
- „Później” odkłada ponowne okno w bieżącej sesji; „Sprawdź teraz” może ponownie otworzyć prompt.
- Alternatywa: **„Wybierz APK z telefonu”**, jeśli plik został przekazany na czacie albo skopiowany lokalnie. Nadal sprawdzany jest podpis i numer wersji, choć nie ma SHA manifestu, bo plik pochodzi z wyboru użytkownika.
- Beta ma uprawnienia INTERNET i REQUEST_INSTALL_PACKAGES. Wariant Stable nie deklaruje tych uprawnień ze względu na tę funkcję.
- Lokalna diagnostyka `UPDATE_CHECK_FAILED`, `UPDATE_AVAILABLE`, `UPDATE_DOWNLOAD_STARTED`, `UPDATE_APK_VERIFIED`, `UPDATE_APK_REJECTED`, `UPDATE_INSTALLER_OPENED` itp., bez logowania URL z tokenami (nie wpisywać tokenów).

## Czego NIE deklarujemy jako gotowe

1. Działającego hostingu manifestu/APK dla prywatnego EDHOME. Adres GitHub Actions wymaga autoryzacji i nie nadaje się do wklejenia w aplikację; nie wkładać prywatnego tokenu do APK.
2. Produkcyjnego i trwałego klucza podpisu / gwarancji przejścia z wcześniejszych debug APK bez konfliktu. Odinstalowanie usuwa aktualne lokalne dane; eksport bazy nadal do zrobienia.
3. Pełnej integracji Stable z Google Play In-App Updates: obecna Stable to prototyp nieopublikowany w Play; przycisk otwiera Play, lecz sam nie potwierdza nowej wersji.
4. Automatycznych testów instalowania na fizycznych urządzeniach / zachowania całych danych podczas przejścia z poprzednich instalacji.

**Żeby zamknąć funkcję zgodnie z ustaleniami:** prywatny kanał DEV potrzebuje bezpiecznego miejsca publikacji plików HTTPS, testowego stałego klucza podpisu i testu aktualizacji ze starej instalacji **bez utraty bazy**. Stable wymaga integracji z Play po opublikowaniu.

[Roadmapa](ROADMAP.md) · [Specyfikacja](SPECYFIKACJA_CALOSC.md).
