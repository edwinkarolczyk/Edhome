# EDHOME 0.1.0-beta.1 — pierwszy szkielet

> **Kod beta, nie ukończony produkt.** Zmiany tylko na gałęzi `beta`; nie przenosić danych finansowych ani prywatnych do tej wersji. Nie mieszać instalacji DEV z przyszłą wersją Stable.

## Co działa w kodzie

- Jedna natywna aplikacja Android dla telefonu i tabletu (układ przewijalny, duże kafelki).
- Konfiguracja PIN przy pierwszym uruchomieniu i blokada po ponownym uruchomieniu; PIN zapisany jako PBKDF2 z losową solą, bez przechowywania jawnego kodu.
- Własna nazwa gospodarstwa i motywy Grafitowy / Leśny / Jasny.
- Lokalna lista prostych czynności: dodawanie, odhaczanie, usuwanie po dłuższym przytrzymaniu, SQLite.
- Lokalny prototyp spiżarni: dodawanie produktu i przyciski +1/−1; stan liczbowy. To **nie jest** jeszcze pełny magazyn ani finalny model remanentu.
- Ekran modułów „Kalendarz”, „Skaner/Remanent” wyraźnie oznaczony jako zaplanowany.
- Brak uprawnienia INTERNET i brak synchronizacji z chmurą.

## Czego jeszcze NIE ma

APK nie jest częścią kodu repo; należy zbudować go w GitHub Actions lub Android Studio. Nie ma aparatu/skanowania, kreatora remanentu, sezonów, reguł powtarzania, lokalnych powiadomień, integracji bankowej, PayCheck, SUPLA, eksportu/kopii, synchronizacji Wi-Fi ani gotowej obsługi prywatnych profili. PIN w tej wersji to lokalna blokada prototypu, nie kompletny system uprawnień dla gospodarstwa.

## Jak pobrać APK po udanym buildzie GitHub Actions

Na GitHub: **Actions → EDHOME Beta APK → najnowszy zielony build → Artifacts → EDHOME-0.1.0-beta.1-apk**. Po pobraniu rozpakować ZIP i zainstalować `app-debug.apk` na Androidzie. Android może poprosić o zgodę na instalację z wybranego źródła. Debug APK jest tylko do testów; nie dystrybuować publicznie jako produkcyjnego wydania. Jeśli workflow nie uruchomi się lub nie przejdzie, nie ma działającego APK do pobrania.

## Alternatywa — Android Studio

Otwórz repo/gałąź `beta` w Android Studio z SDK 35 i JDK 17; zsynchronizuj Gradle i uruchom `app`. Skrypt GitHub Actions używa Gradle 8.10.2 i AGP 8.7.3. W repo nie ma jeszcze wrappera Gradle; w środowisku lokalnym można go utworzyć albo użyć wbudowanej obsługi IDE.

## Ograniczenia danych testowych

- Baza SQLite jest lokalna na urządzeniu. Odinstalowanie aplikacji usuwa lokalne dane; backup/eksport nie jest jeszcze wykonany.
- Zapomniany PIN nie ma opcji odzyskania w tym prototypie. Nie zapisuj rzeczywistych ważnych danych.
- Baza spiżarni to tymczasowy ekran demonstracyjny: nie traktować jej jako produkcyjnego źródła prawdy ani nie migrować bez audytu.
- Zaokrąglony, docelowy zestaw ikon stable/beta wymaga osobnych zasobów; w repo jest tymczasowa ikona wektorowa.
