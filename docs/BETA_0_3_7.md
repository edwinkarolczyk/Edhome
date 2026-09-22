# EDHOME 0.3.7-beta.1 — cisza powiadomień i prostsze układanie kafelków

## Zakres wydania na beta
- Ustawienia → **Cisza powiadomień**: wybierz początek i koniec okna przechodzącego przez północ (domyślnie 22:00–07:00). Zmiana obejmuje zarówno przypomnienia czynności, jak i lokalne minutniki urządzeń; terminy i odliczanie nadal biegną.
- Przy zmianie godzin ciszy EDHOME przelicza alarmy według nowego okna; nie obiecujemy dokładnego doręczenia co do minuty, bo Android może opóźniać powiadomienia.
- Eksport/import JSON przenosi ustawienia godzin ciszy. Starsze kopie nadal używają bezpiecznych wartości domyślnych.
- Ekran główny: **zaokrąglona, klikalna wskazówka „Przytrzymaj: Edytuj / Przesuń”**, która jednym dotknięciem włącza lub wyłącza tryb układania. Przytrzymanie kafelka poza trybem układania otwiera menu **Edytuj kafelek / Przesuń kafelek**; przesunięcie uchwytu `⋮⋮` przenosi kafelek. Zachowano sześć motywów, 9 kafelków i lokalnie zapisywany układ.
- SQLite bez zmiany: **v13**; Beta bez PIN, Stable z PIN. Gałąź `main` nie jest częścią tego wydania.

## Kontrola odbiorcza
1. Zaktualizuj istniejącą Betę bez odinstalowania; przed próbami importu wyeksportuj kopię JSON.
2. Na ekranie głównym przytrzymaj „Czynności”: sprawdź menu edycji i przesuwania. Zmień nazwę/ikonę/kolor; przesuwaj uchwytem. Dotknij zaokrąglonej wskazówki, ułóż kafelki i zakończ układanie. Sprawdź po restarcie.
3. Sprawdź wszystkie motywy, przewijanie, mały ekran i skalowanie tekstu.
4. Zmień ciszę na 23:00–06:30, przetestuj czynność i minutnik, potem przywróć 22:00–07:00. Zweryfikuj kopię na osobnej instalacji. Test odbioru powiadomień wymaga prawdziwego telefonu i zgód Androida.

CI kompiluje Beta i Stable, sprawdza reguły czasu, migrację, kontrakty interfejsu i certyfikat podpisu APK. Sam build nie potwierdza jakości gestów na fizycznym urządzeniu.
