# EDHOME Beta — uruchomienie publicznego kanału APK

Status: właściciel wyraził zgodę na **publiczne udostępnienie tylko podpisanych APK i manifestu**, bez publikacji prywatnego kodu, PIN-ów, kluczy czy danych domowych.

## Jednorazowe kroki po stronie właściciela GitHuba

1. Na GitHub wybierz **New repository** i utwórz **`edwinkarolczyk/EDHOME-Updates`**, widoczność **Public**, zaznacz **Add a README**, główna gałąź `main`. Nie kopiuj tam źródeł EDHOME ani pliku `.p12`.
2. GitHub → Settings konta → Developer settings → Personal access tokens → Fine-grained tokens → Generate new token. Nadaj mu rozpoznawalną nazwę (np. `EDHOME beta publishing`) i wybierz właściciela `edwinkarolczyk`. W **Repository access** wybierz **Only select repositories → EDHOME-Updates**. **Repository permissions → Contents: Read and write**; Metadata: Read (domyślne). Nie przydzielaj mu dostępu do prywatnego repozytorium EDHOME. Zapisz token bezpiecznie. Ustal rozsądny termin ważności i odnów przed jego upływem.
3. Otwórz prywatne `Edhome` → Settings → Secrets and variables → Actions → **New repository secret**. Nazwa: **`EDHOME_PUBLIC_CHANNEL_TOKEN`**. Wartość: token z poprzedniego kroku. Wklej go tylko do formularza GitHub; nigdy do kodu, czatu, zmiennych Actions ani manifestu. Sekrety podpisu EDHOME pozostają bez zmian.
4. W prywatnym `Edhome` → Actions → **EDHOME Beta APK** → Run workflow → Branch: **beta** → Run workflow. Workflow buduje i weryfikuje podpis, publikuje APK w publicznym wydaniu `EDHOME-Updates` i dopiero na końcu podmienia manifest. Jeśli tokenu nie ma, kończy bez publikacji pliku publicznego i wypisuje komunikat w logu.
5. Potwierdź dostępność poniższych zasobów **bez logowania, także w przeglądarce incognito**:
   - [Lista wydań APK](https://github.com/edwinkarolczyk/EDHOME-Updates/releases)
   - [Manifest JSON](https://raw.githubusercontent.com/edwinkarolczyk/EDHOME-Updates/main/beta-manifest.json)
   - Link `apkUrl` wewnątrz manifestu zwraca rzeczywisty plik APK (bez loginu).
6. Zainstaluj podpisaną Beta z wbudowanym kanałem. Przy pierwszej publikacji numer z manifestu może być **taki sam**, jak zainstalowany: wtedy aplikacja prawidłowo pokaże, że jest aktualna. Następne wydanie z **wyższym versionCode** wyzwoli automatyczne pobranie; Beta pokazuje tylko „Aktualizuj”, instalację finalnie zatwierdza Android.

## Adres używany przez EDHOME

`https://raw.githubusercontent.com/edwinkarolczyk/EDHOME-Updates/main/beta-manifest.json`

Kanał jest domyślnie wpisany do podpisanego wydania Beta od 0.2.2. Wcześniejsza pusta preferencja z 0.1.8/0.2.1 nie ma prawa zablokować wbudowanego kanału. Dopóki publiczne repo nie istnieje lub nie ma manifestu, przycisk „Sprawdź” może zwrócić błąd HTTPS/404 — **sama konfiguracja nie oznacza działającej aktualizacji**.

## Gdy wystąpi błąd

- `EDHOME_PUBLIC_CHANNEL_TOKEN is missing`: dodaj sekret do prywatnego repo. Nie wysyłaj go nikomu.
- 404 repo: upewnij się, że nazwa to dokładnie `EDHOME-Updates` i widoczność to Public.
- 403 API: sprawdź, czy token ma dostęp **tylko do EDHOME-Updates** oraz Contents read/write; nie zwiększaj niepotrzebnie uprawnień.
- Istniejący tag wydania: nowe APK wymaga zwiększenia `versionCode`/`versionName`; nie podmieniaj wcześniej opublikowanego podpisanego pliku pod tym samym numerem.
- Brak nowej aktualizacji: `versionCode` manifestu musi być większy od zainstalowanego, pakiet i podpis muszą się zgadzać, a SHA-256 musi pasować.
- Token wygasł: odnów go i podmień wyłącznie sekret Actions; sama aplikacja nie posiada tokenu i nadal pobiera już publiczne wydania.

**Prywatne repo EDHOME pozostaje prywatne. Publiczne repo zawiera tylko binaria APK, publiczny manifest i techniczny README.**
