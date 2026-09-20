# EDHOME 0.2.4-beta.1 — prawdziwy stan aktualizacji

Gałąź beta; versionCode 14. Jest to korekta błędnego komunikatu wersji 0.2.2/0.2.3.

## Co było nie tak
W EDHOME 0.2.2 adres publicznego manifestu był zaszyty, mimo że publiczne repozytorium `edwinkarolczyk/EDHOME-Updates` jeszcze nie istniało, a sekret publikowania `EDHOME_PUBLIC_CHANNEL_TOKEN` nie był dodany. Aplikacja błędnie pokazywała „Źródło aktualizacji skonfigurowane” i sugerowała użytkownikowi samodzielną konfigurację HTTPS. Sam adres to nie istniejący kanał. Nie jest to błąd telefonu ani użytkownika.

Trener 2 i WMM publikują podpisane APK przez GitHub Releases w swoich **publicznych** repozytoriach, dlatego mają dostępne bez hasła zasoby. EDHOME to repozytorium **prywatne**; bez publicznego repozytorium dystrybucji lub osobnego publicznego hostingu nie można bezpiecznie zaimplementować tego samego dostępu po stronie telefonu. Nie wolno wpisywać tokenu prywatnego repo do APK ani publikować tam kluczy.

## Co poprawiono
- Aplikacja odróżnia wpisany adres od **potwierdzonego, poprawnego manifestu**.
- HTTP 404/410 wyjaśnia, że kanał nie został opublikowany; nie każe użytkownikowi szukać/ustawiać adresu HTTPS.
- Inne błędy połączenia mówią o problemie z dostępnością, bez sugerowania wpisywania URL.
- Na ekranie aktualizacji brak zielonego „źródło skonfigurowane”, dopóki poprawny manifest nie został odczytany.
- GitHub Actions zapisuje jawne ostrzeżenie w podsumowaniu, jeśli brakuje sekretu publikacji. Podpisane APK jest nadal dostępne jako artefakt **prywatnego** przebiegu.
- Numer wersji: 0.2.4-beta.1, versionCode 14; stary versionCode 12 nie może zostać ponownie wydany jako nowa aktualizacja.

## Jednorazowa bariera infrastruktury
Publiczne repo `EDHOME-Updates` (zawierające tylko APK i manifest) i sekret `EDHOME_PUBLIC_CHANNEL_TOKEN` z prawem Contents: read/write **wyłącznie do tego publicznego repo**. W obecnym połączeniu GitHub nie ma akcji tworzenia repozytorium ani wpisania sekretu; krok właściciela trzeba wykonać jednorazowo na GitHub. Użytkownik **nie podaje adresu w aplikacji**. Kod Beta ma go już na stałe. Gdy kanał zacznie działać, każde kolejne podpisane wydanie z wyższym versionCode zostanie wykryte i pobrane przez EDHOME; instalację zatwierdzi Android.

## Aktualizacja przejściowa na telefonie
GitHub → prywatne `edwinkarolczyk/Edhome` → Actions → `EDHOME Beta APK` → pomyślny przebieg wersji 0.2.4 → Artifacts → `EDHOME-0.2.4-beta.1-signed-apk`; rozpakuj ZIP i wskaż APK przez `Aktualizacje → Instaluj APK`, bez usuwania starej aplikacji. Przed aktualizacją eksportuj JSON i przechowuj plik poza aplikacją. To **nie** jest przetestowana instalacja na fizycznym telefonie.
