# EDHOME Desktop Beta

Pierwszy desktopowy klient EDHOME dla Windows.

## Zakres MVP
- osobny interfejs PC z lewym panelem,
- podstawowy podgląd: Pulpit, Dzisiaj, Kalendarz, Zadania, Magazyn, Spiżarnia, Zakupy, PayCheck, Pojazdy, Odpady, Miejsca, Ustawienia,
- pobieranie pełnego snapshotu z EDHOME Beta na Androidzie po domowej sieci Wi‑Fi/LAN,
- kod parowania zapisany lokalnie na telefonie,
- cache snapshotu na PC, więc po pobraniu dane można przeglądać offline,
- ręczny import standardowego backupu JSON.

## Bezpieczeństwo pierwszej wersji
Desktop jest **read-only**. Nie zapisuje jeszcze zmian do Androida. Dzięki temu nie ma ryzyka utraty danych przy równoczesnej edycji na kilku urządzeniach. Prywatny PayCheck nie jest częścią zwykłego DataBackup i nie jest wystawiany do desktopowego endpointu.

## Uruchomienie
GitHub Actions buduje paczkę Windows z dołączonym runtime Java. Po rozpakowaniu uruchom EDHOME-Desktop-Beta.exe.

Na telefonie wejdź w Ustawienia → EDHOME Desktop • Wi‑Fi, a następnie przepisz adres oraz kod parowania do ustawień programu PC.
