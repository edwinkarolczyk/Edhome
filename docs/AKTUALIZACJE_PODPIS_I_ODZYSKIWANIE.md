# EDHOME — naprawa aktualizacji i podpisu Beta DEV (19.09.2026)

## Potwierdzona przyczyna błędu Androida

Użytkownik pokazał zainstalowany **EDHOME 0.1.2-beta.1 / versionCode=3** oraz komunikat Androida „Aplikacja nie została zainstalowana, bo powoduje konflikt z istniejącym pakietem”. Potwierdzono, że APK 0.1.1 i 0.1.2 z GitHub Actions są podpisane **różnymi certyfikatami Android Debug**:

- 0.1.1: SHA-256 certyfikatu `48:F5:90:4F:B4:1D:6E:B1:0F:81:2F:7C:8C:75:01:A1:C2:D9:FD:A0:24:AB:25:52:34:E2:63:9E:C2:CE:D1:3B`.
- 0.1.2, konkretny artefakt z run 35447855806: SHA-256 certyfikatu `4C:A1:9E:E1:69:9C:E7:97:6C:AC:D2:86:73:86:C0:17:8C:09:BD:A5:0A:B0:1D:17:6C:29:0B:13:28:3B:BE:FD`.

Nie należy zakładać, że wszystkie archiwa nazwane 0.1.2 mają ten sam podpis: workflow wykonywał **wiele kompilacji z tym samym versionCode**, a cache debug.keystore zwracał „Cache not found” i „Cache is read-only: will not save state”. **Z samego APK nie można odzyskać prywatnego klucza podpisu**. Zwiększenie `versionCode`, zmiana ikony i inne zmiany kodu nie usuwają konfliktu podpisu.

**Nie odinstalowywać starej instalacji, jeśli zawiera dane, które użytkownik chce zachować.** Ponowna instalacja nowej aplikacji z innym podpisem wymaga najpierw skopiowania starych danych i bezpiecznego planu przywrócenia. Sam APK ani log diagnostyczny nie zawiera bazy spiżarni i czynności.

## Jak naprawiono proces CI

- CI **nie publikuje już losowo podpisanych APK Debug**. Nie polega na GitHub cache jako trwałym magazynie klucza.
- Nowy kanał wymaga czterech sekretów GitHub Actions: `EDHOME_BETA_KEYSTORE_B64`, `EDHOME_BETA_KEY_ALIAS`, `EDHOME_BETA_STORE_PASSWORD`, `EDHOME_BETA_KEY_PASSWORD`. Bez nich CI może skompilować kod, lecz **celowo zakończy się błędem przed publikacją APK**.
- Beta DEV będzie budowana jako podpisany wariant `betaRelease`, a Stable kompilowana kontrolnie, bez publikacji. Plik klucza rozpakowywany tylko do katalogu tymczasowego runnera i usuwany po zadaniu.
- Aktualizator wymaga tej samej tożsamości pakietu i certyfikatu co zainstalowana aplikacja. Nowy stały klucz **nie naprawi zgodności wstecz z APK podpisanym zaginionym kluczem**.
- Nowa wersja w konfiguracji projektu: `0.1.4-beta.1` / `versionCode 5`. **Nie nazywać jej wydaną ani gotową do instalacji, dopóki nie powstanie podpisany, zweryfikowany artefakt i nie ma decyzji co do zachowania danych.**

## Konfiguracja trwałego podpisu — wykonuje właściciel repozytorium

Utwórz **raz**, na swoim komputerze, klucz podpisu tylko dla Beta DEV i bezpieczną kopię offline. Nie wysyłaj go do czatu i nie dodawaj do repozytorium.

**EDHOME Beta DEV ma już nowy certyfikat (19.09.2026).** Prywatny plik `edhome-beta.p12`, hasło i Base64 zostały przekazane właścicielowi poza repozytorium. Nie generuj kolejnego klucza zamiast niego, ponieważ kolejna zmiana certyfikatu zerwie możliwość aktualizacji.

Oczekiwany publiczny odcisk certyfikatu SHA-256:

`40:E8:EF:84:39:F2:67:76:A4:F4:95:E6:B3:95:1C:72:E8:75:9E:D9:0F:A2:6D:9A:47:6A:AC:0A:58:9A:A6:EE`

Zachowaj przekazany `.p12` i hasła offline. Do GitHub Secrets wklej Base64 i hasła z przekazanych plików. **Nie wrzucaj prywatnych danych do repozytorium.** Pomocniczy [skrypt Windows](../tools/setup-beta-signing.ps1) tylko sprawdza certyfikat i pomaga skopiować Base64; nie generuje już kolejnej tożsamości podpisu. Aby użyć skryptu, umieść przekazany `.p12` w Dokumenty/EDHOME-Keys, pobierz repo i uruchom:

```powershell
powershell -ExecutionPolicy Bypass -File .\\tools\\setup-beta-signing.ps1 -CopyBase64
```

Następnie GitHub → repo Edhime → **Settings → Secrets and variables → Actions → New repository secret**. Zapisz `EDHOME_BETA_KEYSTORE_B64` (wartość ze schowka) i pozostałe trzy sekrety (alias i oba hasła). Utrzymuj bezpieczny prywatny backup oryginalnego `.p12` oraz haseł: utrata klucza ponownie uniemożliwi zwykłe aktualizacje. Nie używaj sekretów produkcyjnych Stable w wersji Beta. W przypadku polityk organizacji sprawdź, czy Actions ma dostęp do sekretów.

**Nie zmieniaj obecnej instalacji w telefonie w trakcie przygotowywania klucza.** Najpierw wybierz, czy dane testowe muszą zostać zachowane. Gdy tak, przed odinstalowaniem wymagany jest zweryfikowany backup danych aplikacji z bieżącej instalacji (np. przez ADB `run-as` dla debugowalnej aplikacji, o ile działa na telefonie) i przetestowany sposób przywrócenia. Plik wyeksportowanej diagnostyki NIE jest backupem. ADB i operacje odtwarzania należy przeprowadzać razem z użytkownikiem krok po kroku, nie zakładać automatycznego powodzenia.

## Błędny link w ekranie Aktualizacje

Adres `https://github.com/edwinkarolczyk/Edhime/actions/runs/35447855806` jest **stroną HTML pojedynczej kompilacji**, a nie manifestem JSON ani źródłem APK. Dlatego nie należy go wklejać do pola Aktualizacji. Kod przyszłej wersji odrzuca taki link i ma przycisk wyłączenia sieciowego sprawdzania.

Prawidłowy kanał Beta DEV potrzebuje **trwałego, dostępnego bez logowania HTTPS** manifestu JSON oraz podpisanego APK z rzeczywiście sprawdzonym SHA-256. Repozytorium źródłowe może pozostać prywatne, ale trzeba odrębnie uzgodnić, gdzie udostępniać użytkownikowi binarne wydania. **Nie używać PAT GitHub w aplikacji.** Na razie brak skonfigurowanego zdalnego kanału: ręczny import APK z telefonu jest w kodzie, ale także nie obejdzie Androidowego wymogu zgodności podpisu.

## Następne kroki

1. Nie wysyłać „kolejnego naprawionego APK” bez trwałego podpisu.
2. Ustalić, czy użytkownik chce zachować testowe dane 0.1.2. Jeśli tak, pomóc wykonać i zweryfikować kopię z urządzenia przed jakimkolwiek odinstalowaniem.
3. Skonfigurować cztery sekrety podpisu (użytkownik robi to w GitHub).
4. Zbudować raz `0.1.4-beta.1`, porównać certyfikat w następnym wydaniu i przetestować aktualizację z tej wersji na tę samą podpisaną linię — bez utraty danych.
5. Ustalić serwer binarnego kanału DEV i wdrożyć rzeczywisty manifest HTTPS, a dopiero potem włączyć częste zdalne sprawdzanie w aplikacji.

[Opis 0.1.2](BETA_0_1_2.md) · [roadmapa](ROADMAP.md) · [pełna specyfikacja](SPECYFIKACJA_CALOSC.md).
