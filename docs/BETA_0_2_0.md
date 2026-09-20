# EDHOME 0.2.0-beta.1 — miejsca, przypisane czynności i kanał aktualizacji

**Wersja aplikacji:** `0.2.0-beta.1`, `versionCode=10`, SQLite 4.
**Gałąź:** `beta`, `main` bez zmian. To nadal Beta do testowania.

## Nowe funkcje gospodarstwa
- Moduł **Miejsca**: np. Dom, Ogród, Garaż, Warsztat; edycja nazwy i rodzaju, lista przypisanych czynności.
- Czynności są nadal samodzielne. Nowe pole **Miejsce (opcjonalnie)** w edytorze; można zostawić „Bez przypisanego miejsca”.
- Usunięcie miejsca odpina powiązanie, **nie usuwa** czynności i historii. SQLite wykonuje to w transakcji.
- Migracja SQLite 3→4 dodaje tabelę `places` i nullable `tasks.place_id`. Migracje 1→2→3→4 działają sekwencyjnie bez `DROP TABLE`.
- Eksport JSON v4 obejmuje miejsca, ich rodzaje i przypisania. Import wspiera kopie v2/v3/v4; dla starych kopii brak miejsc, stare czynności pozostają samodzielne. Spójność identyfikatorów miejsc jest sprawdzana przed zastąpieniem bazy.

## Aktualizator
- Uproszczone, zaokrąglone komunikaty EDHOME; ekran pokazuje również `versionCode`, aby było jasne, czy instalacja faktycznie się zmieniła.
- Możliwość wpisania **publicznego HTTPS źródła** do APK już w czasie budowania — bez ręcznej konfiguracji na każdym telefonie. Ręczny adres w opcjach zaawansowanych nadal działa i może wyłączyć domyślne źródło.
- Przy wykryciu pobranego i poprawnie podpisanego nowszego APK Beta wymaga **Aktualizuj**, bez „Później”; Stable zachowuje odroczenie przez Play.
- Podpis i wymóg zgody Androida na instalację pozostają bez zmian.
- **Samo zainstalowanie tego APK nie włącza automatyki**, dopóki nie ma opublikowanego publicznego manifestu i APK. Budowa ze zmienną publicznego kanału wymaga przyszłego wydania o wyższym `versionCode`.

## Osobne, opcjonalne publiczne wydania plików — kod prywatny
Źródłowe `Edhome` pozostaje **prywatne**. Przygotowano automatyczne publikowanie **wyłącznie podpisanego APK i manifestu** w odrębnym PUBLICZNYM repozytorium wydań. Bez wyraźnego wyboru i konfiguracji właściciela krok jest WYŁĄCZONY; workflow dalej zapisuje prywatny artefakt.

Jednorazowa konfiguracja właściciela, **jeśli zgodzi się na publiczną dostępność samego APK**:

1. Stwórz osobne publiczne repozytorium GitHub (np. `edwinkarolczyk/Edhome-Updates`) z gałęzią `main`. Nie przenoś tam kodu źródłowego ani kluczy.
2. W **prywatnym** `Edhome`, Settings → Secrets and variables → Actions → Variables ustaw `EDHOME_PUBLIC_CHANNEL_REPO` na dokładne `owner/public-repo`. Opcjonalnie `EDHOME_BETA_NOTES` z opisem zmian.
3. W Secrets prywatnego repo ustaw `EDHOME_PUBLIC_CHANNEL_TOKEN` na token ograniczony do **Contents: Read and write** wyłącznie osobnego repo wydań; sekret nigdy nie trafia do APK.
4. Kolejny commit z **nowym numerem wersji**: CI wbuduje adres `https://raw.githubusercontent.com/owner/public-repo/main/beta-manifest.json`, podpisze APK, zweryfikuje certyfikat, umieści APK jako release `beta-vX.Y.Z-beta.1`, a następnie zaktualizuje manifest. Jeśli publikacja nie uda się, nie zastępuje manifestu „latest” nieistniejącym APK.
5. Przetestuj pobranie manifestu i APK w przeglądarce bez logowania; dopiero potem sprawdź w EDHOME pobieranie i systemowe zatwierdzenie instalacji.

**Nie wpisuj** linku do prywatnego GitHub Actions w polu adresu. Zmienne i sekret służą do publikowania binariów, a nie do otwierania prywatnego repozytorium w telefonie. Repo aktualizacji ma być publiczne tylko wtedy, gdy właściciel zatwierdzi taki sposób dystrybucji.

## Sprawdzenie na telefonie
1. Eksportuj kopię aktualnych danych z 0.1.7/0.1.8, **nie odinstalowuj** poprzedniej instalacji.
2. Zainstaluj 0.2.0 i sprawdź PIN, remanent, spiżarnię, zadania, kalendarz.
3. Dodaj miejsce „Ogród” → dodaj czynność „Zebrać winogrona”, ustaw miejsce „Ogród”. Sprawdź listę i edycję.
4. Usuń miejsce, sprawdź, że czynność i historia nie zniknęły, a powiązanie jest puste.
5. Eksportuj kopię v4, przywróć w testowym stanie, sprawdź miejsca i przypisania; przetestuj kopię v3.
6. W Aktualizacjach upewnij się, że widoczny jest faktycznie nowy numer `versionCode=10`. Jeśli źródła HTTPS nie skonfigurowano, aplikacja ma to pokazywać jawnie, a nie obiecywać automatyki.
