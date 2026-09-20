# EDHOME 0.1.6-beta.1 — wymagana aktualizacja w Beta DEV

Wariant Beta: `versionCode=7`, `versionName=0.1.6-beta.1`. Stable zachowuje osobną ścieżkę Google Play.

## Zasada UX

- **Beta:** gdy dostępne jest nowe, pobrane i poprawnie zweryfikowane APK, wyświetl ciemne, zaokrąglone okno nad aplikacją z jedyną akcją **Aktualizuj**. Nie ma **Później**, zamykania przez Back ani stuknięciem poza okno. Każde kolejne poprawne wydanie Beta ma pierwszeństwo przed dalszym korzystaniem z bieżącej wersji.
- **Stable:** okno Google Play ma **Aktualizuj** i **Później**. Tylko Stable pozwala odroczyć aktualizację.
- **Android:** zgoda na rzeczywistą instalację musi być wyrażona w systemie; aplikacja nie obchodzi ekranu instalatora, nie instaluje APK po cichu.
- **Brak internetu lub manifestu:** nie blokować aplikacji w nieskończoność i nie udawać, że aktualizacja istnieje. Reguła pierwszeństwa Beta uruchamia się dopiero po pobraniu i pomyślnej weryfikacji nowego APK.
- **Bezpieczne wydania:** tylko wyższy `versionCode`, identyczny pakiet, zgodny podpis i poprawny skrót SHA-256 z manifestu; ręczny import wymaga zgodności pakietu/podpisu i rosnącego numeru.
- **Kanał automatyczny:** aktualizator i częste odpytywanie są w kodzie, ale bez publicznie dostępnego źródła HTTPS manifestu i APK nie zadziała automatyczne pobieranie. Nie wpisywać tokenów w apk. Wydanie 0.1.6 samo w sobie nie uruchamia hostingu.

## Odbiór na telefonie

1. Zainstaluj podpisane APK 0.1.6-beta.1 na 0.1.5-beta.1 i sprawdź, że dane pozostały.
2. Po rzeczywistym udostępnieniu następnego wydania z tym samym certyfikatem i manifestem HTTPS, uruchom Beta, poczekaj na pobranie.
3. Zobacz okno z jednym przyciskiem **Aktualizuj**; Back i dotknięcie poza nim nie powinny go zamykać.
4. Potwierdź instalację w Androidzie; po aktualizacji numer musi się zwiększyć, a czynności, kalendarz i remanent mają pozostać.
5. Stable w aktualizacji Play nadal pokazuje **Później** (dopiero po publikacji i testach wydania sklepowego).
