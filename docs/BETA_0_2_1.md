# EDHOME 0.2.1-beta.1 — kafelki 3 × 3, przesuwanie pionowe

Gałąź `beta`. Wersja `0.2.1-beta.1`, `versionCode 11`.

## Zmiana interfejsu

- Panel główny i ekran Aktualizacje mają siatkę **3 kolumny × 3 rzędy**, czyli **9 kwadratowych, zaokrąglonych kafelków**.
- Nie ma już poziomego paska kafli ani obciętej karty z prawej. Ekran przewija się wyłącznie **w górę i w dół** wraz z kartami informacyjnymi.
- Kafelki dostosowują bok do szerokości oraz wysokości urządzenia; przy niewielkim ekranie cała strona pozostaje przewijana pionowo. Na wąskim ekranie nie deklarujemy, że wszystkie karty i dziewięć kafli fizycznie mieszczą się naraz bez przewijania.
- Panel: Czynności, Kalendarz, Miejsca, Spiżarnia, Remanent, Aktualizacje, Kopia danych, Ustawienia, Na dziś.
- Aktualizacje: Sprawdź aktualizację, APK z telefonu (Stable: Google Play), Pobrany APK (Stable: Kanał Stable), Kopia danych, Opcje zaawansowane, Status kanału, Wersja aplikacji, Co nowego, Panel główny.
- Beta nadal ma obowiązkową instalację po prawidłowym pobraniu i weryfikacji nowego APK; Stable ma możliwość odroczenia w Google Play.

## Test na Androidzie

1. Eksportuj kopię danych, a następnie zainstaluj podpisane APK 0.2.1-beta.1 **na** poprzednią wersję bez odinstalowywania.
2. Wejdź na Panel: liczba kafli = 9, każdy kwadratowy, 3 w rzędzie, trzy rzędy. Przewijaj wyłącznie góra/dół; sprawdź „Na dziś”.
3. Wejdź w Aktualizacje: także 3 × 3. Zajrzyj do Statusu kanału i Wersji aplikacji.
4. Przełącz motyw jasny i ciemny; sprawdź widoczność i brak obcinania podpisów. Na bardzo małym ekranie przewiń dół.
5. Sprawdź zachowanie danych i aktualizacji. Samo wydanie APK **nie uruchamia** serwera HTTPS, jeśli publiczny kanał nie został skonfigurowany.
